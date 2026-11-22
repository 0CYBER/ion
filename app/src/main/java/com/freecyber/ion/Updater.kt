package com.freecyber.ion

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    /** Manual check found nothing newer; the automatic check stays silent instead. */
    data object UpToDate : UpdateState
    data class Available(val release: ReleaseInfo, val manual: Boolean) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float?) : UpdateState
    /** Downloaded and verified; waiting for the rider to allow installs from Ion. */
    data class NeedsInstallPermission(val release: ReleaseInfo) : UpdateState
    data class Failed(val reason: Reason, val release: ReleaseInfo?) : UpdateState
    enum class Reason { NETWORK, DOWNLOAD, INVALID_APK }
}

/**
 * Checks GitHub releases once per launch (if enabled) or on demand, downloads the APK into the cache and
 * hands it to the system installer. The installer itself enforces the Ion signature; before that the APK's
 * size, package name and build are checked so a wrong file never reaches it.
 */
class Updater(private val context: Context, private val prefs: AppPrefs, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = mutable.asStateFlow()
    private var launchChecked = false
    private var job: Job? = null

    val currentBuild: Long = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }.getOrDefault(0L)
    val currentVersion: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    private val apkFile get() = File(context.cacheDir, "updates/ion-update.apk")

    /** Once per process: rotations and returning to the app do not ask again. */
    fun checkOnLaunch() {
        if (launchChecked) return
        launchChecked = true
        if (prefs.updates.value.autoCheck) check(manual = false)
    }

    fun check(manual: Boolean) {
        if (job?.isActive == true) return
        job = scope.launch {
            mutable.value = UpdateState.Checking
            val releases = withContext(Dispatchers.IO) { runCatching { fetchReleases() }.getOrNull() }
            prefs.setUpdateCheckedAt(System.currentTimeMillis())
            if (releases == null) {
                mutable.value = if (manual) UpdateState.Failed(UpdateState.Reason.NETWORK, null) else UpdateState.Idle
                return@launch
            }
            val config = prefs.updates.value
            val found = Updates.pick(releases, config.channel, currentBuild)
            mutable.value = when {
                found != null -> UpdateState.Available(found, manual)
                manual -> UpdateState.UpToDate
                else -> UpdateState.Idle
            }
        }
    }

    /** Closes the dialog; a running download is only stopped through [cancelDownload]. */
    fun dismiss() { if (mutable.value !is UpdateState.Downloading) mutable.value = UpdateState.Idle }

    fun cancelDownload() { job?.cancel(); mutable.value = UpdateState.Idle }

    fun download(release: ReleaseInfo) {
        job?.cancel()
        job = scope.launch {
            mutable.value = UpdateState.Downloading(release, null)
            val ok = withContext(Dispatchers.IO) {
                runCatching { fetchApk(release) { p -> mutable.value = UpdateState.Downloading(release, p) } }.getOrDefault(false)
            }
            if (!isActive) return@launch
            if (!ok) { mutable.value = UpdateState.Failed(UpdateState.Reason.DOWNLOAD, release); return@launch }
            if (!verified(release)) { apkFile.delete(); mutable.value = UpdateState.Failed(UpdateState.Reason.INVALID_APK, release); return@launch }
            install(release)
        }
    }

    /** Called on resume: after the rider allowed installs from Ion in system settings, continue right away. */
    fun resumeInstall() {
        val waiting = mutable.value as? UpdateState.NeedsInstallPermission ?: return
        if (canInstall() && apkFile.exists()) install(waiting.release)
    }

    fun openInstallPermissionSettings() {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openReleasePage(release: ReleaseInfo?) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release?.pageUrl ?: Updates.RELEASES_PAGE))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun canInstall() = context.packageManager.canRequestPackageInstalls()

    private fun install(release: ReleaseInfo) {
        if (!canInstall()) { mutable.value = UpdateState.NeedsInstallPermission(release); return }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val started = runCatching { context.startActivity(intent) }.isSuccess
        mutable.value = if (started) UpdateState.Idle else UpdateState.Failed(UpdateState.Reason.INVALID_APK, release)
    }

    private fun verified(release: ReleaseInfo): Boolean {
        val file = apkFile
        if (!file.exists() || (release.apkSize > 0 && file.length() != release.apkSize)) return false
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0) ?: return false
        val build = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        return info.packageName == context.packageName && build == release.build.toLong()
    }

    private fun connection(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000; readTimeout = 20_000; instanceFollowRedirects = true
        // Generic client name: no user or device details go to GitHub.
        setRequestProperty("User-Agent", "Ion-Android-Updater")
    }

    private fun fetchReleases(): List<ReleaseInfo> {
        val conn = connection(Updates.RELEASES_API)
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return parseReleases(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect() }
    }

    private fun fetchApk(release: ReleaseInfo, progress: (Float?) -> Unit): Boolean {
        val file = apkFile
        file.parentFile?.mkdirs(); file.delete()
        val conn = connection(release.apkUrl)
        conn.setRequestProperty("Accept", "application/octet-stream")
        try {
            if (conn.responseCode != 200) return false
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize.takeIf { it > 0 }
            var read = 0L; var lastReported = -1
            conn.inputStream.use { input -> file.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    out.write(buffer, 0, n); read += n
                    val percent = total?.let { (read * 100 / it).toInt() }
                    if (percent != lastReported) { lastReported = percent ?: 0; progress(total?.let { read.toFloat() / it }) }
                }
            } }
            return true
        } finally { conn.disconnect() }
    }

    companion object {
        /** GitHub releases JSON → releases that carry an `ion-<build>.apk` asset; drafts are ignored. */
        fun parseReleases(json: String): List<ReleaseInfo> {
            val array = JSONArray(json)
            return (0 until array.length()).mapNotNull { i ->
                val r = array.getJSONObject(i)
                if (r.optBoolean("draft")) return@mapNotNull null
                val assets = r.optJSONArray("assets") ?: return@mapNotNull null
                val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                    .firstOrNull { Updates.buildOf(it.optString("name")) != null } ?: return@mapNotNull null
                ReleaseInfo(
                    tag = r.optString("tag_name"),
                    title = r.optString("name").ifBlank { r.optString("tag_name") },
                    build = Updates.buildOf(apk.getString("name"))!!,
                    prerelease = r.optBoolean("prerelease"),
                    apkUrl = apk.getString("browser_download_url"),
                    apkSize = apk.optLong("size"),
                    notes = r.optString("body"),
                    pageUrl = r.optString("html_url").ifBlank { Updates.RELEASES_PAGE },
                )
            }
        }
    }
}
