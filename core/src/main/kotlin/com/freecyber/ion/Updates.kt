package com.freecyber.ion

/** Which GitHub releases the updater offers: full releases only, or every CI prerelease too. */
enum class UpdateChannel { STABLE, PRERELEASE }

data class UpdateConfig(
    val channel: UpdateChannel = UpdateChannel.STABLE,
    /** Check once per app launch and offer the update in a dialog. */
    val autoCheck: Boolean = true,
)

/** One GitHub release that carries an installable Ion APK. */
data class ReleaseInfo(
    val tag: String,
    val title: String,
    /** versionCode of the APK, read from its asset name `ion-<build>.apk`. */
    val build: Int,
    val prerelease: Boolean,
    val apkUrl: String,
    val apkSize: Long,
    val notes: String,
    val pageUrl: String,
)

object Updates {
    /** CI names the asset after the build so the updater never has to download an APK to learn its version. */
    val APK_ASSET = Regex("""ion-(\d+)\.apk""")
    const val RELEASES_API = "https://api.github.com/repos/0CYBER/ion/releases?per_page=30"
    const val RELEASES_PAGE = "https://github.com/0CYBER/ion/releases"

    fun buildOf(assetName: String): Int? = APK_ASSET.matchEntire(assetName)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * Newest release newer than [currentBuild] on [channel]. A build the rider chose to skip is not offered
     * again by the automatic check, but anything newer than it is.
     */
    fun pick(releases: List<ReleaseInfo>, channel: UpdateChannel, currentBuild: Long, skippedBuild: Int? = null): ReleaseInfo? =
        releases.asSequence()
            .filter { channel == UpdateChannel.PRERELEASE || !it.prerelease }
            .filter { it.build > currentBuild }
            .filter { skippedBuild == null || it.build > skippedBuild }
            .maxByOrNull { it.build }

    /** Release notes for a dialog: Markdown list markers kept, overly long notes cut at a line break. */
    fun shortNotes(notes: String, limit: Int = 1200): String {
        val clean = notes.replace("\r\n", "\n").trim()
        if (clean.length <= limit) return clean
        val cut = clean.lastIndexOf('\n', limit).takeIf { it > limit / 2 } ?: limit
        return clean.substring(0, cut).trimEnd() + "\n…"
    }
}
