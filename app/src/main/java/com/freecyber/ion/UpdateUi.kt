@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.freecyber.ion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

/** Everything the update UI needs, passed in so screens stay previewable without the real [Updater]. */
internal data class UpdateUi(
    val state: UpdateState = UpdateState.Idle,
    val config: UpdateConfig = UpdateConfig(),
    val checkedAt: Long = 0,
    val version: String = "",
    val build: Long = 0,
)

internal data class UpdateActions(
    val setConfig: (UpdateConfig) -> Unit = {},
    val check: () -> Unit = {},
    val download: (ReleaseInfo) -> Unit = {},
    val dismiss: () -> Unit = {},
    val skip: (ReleaseInfo) -> Unit = {},
    val cancelDownload: () -> Unit = {},
    val openInstallSettings: () -> Unit = {},
    val openPage: (ReleaseInfo?) -> Unit = {},
)

/** Offer, progress, permission and failure in one dialog; nothing is shown while idle or after a silent check. */
@Composable internal fun UpdateDialog(ui: UpdateUi, actions: UpdateActions) {
    when (val s = ui.state) {
        is UpdateState.Available -> AlertDialog(onDismissRequest = actions.dismiss,
            icon = { Icon(Icons.Rounded.SystemUpdate, null) },
            title = { Text(stringResource(R.string.update_title)) },
            text = { ReleaseSummary(s.release, ui.build) },
            confirmButton = { Button(onClick = { actions.download(s.release) }) { Text(stringResource(R.string.update_install)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { actions.skip(s.release) }) { Text(stringResource(R.string.update_skip)) }
                    TextButton(onClick = actions.dismiss) { Text(stringResource(R.string.update_later)) }
                }
            })
        is UpdateState.Downloading -> AlertDialog(onDismissRequest = {},
            icon = { Icon(Icons.Rounded.Download, null) },
            title = { Text(stringResource(R.string.update_downloading)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s.release.title, style = MaterialTheme.typography.bodyMedium)
                    val p = s.progress
                    if (p == null) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                    else {
                        LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        Text("${(p * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, fontFamily = IonSans)
                    }
                }
            },
            confirmButton = { TextButton(onClick = actions.cancelDownload) { Text(stringResource(R.string.cancel)) } })
        is UpdateState.NeedsInstallPermission -> AlertDialog(onDismissRequest = actions.dismiss,
            icon = { Icon(Icons.Rounded.Security, null) },
            title = { Text(stringResource(R.string.update_permission_title)) },
            text = { Text(stringResource(R.string.update_permission_text)) },
            confirmButton = { Button(onClick = actions.openInstallSettings) { Text(stringResource(R.string.update_permission_open)) } },
            dismissButton = { TextButton(onClick = actions.dismiss) { Text(stringResource(R.string.update_later)) } })
        is UpdateState.Failed -> if (s.release != null) AlertDialog(onDismissRequest = actions.dismiss,
            icon = { Icon(Icons.Rounded.ErrorOutline, null) },
            title = { Text(stringResource(R.string.update_failed_title)) },
            text = { Text(stringResource(failureText(s.reason))) },
            confirmButton = { TextButton(onClick = { actions.download(s.release) }) { Text(stringResource(R.string.update_retry)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { actions.openPage(s.release); actions.dismiss() }) { Text(stringResource(R.string.update_open_github)) }
                    TextButton(onClick = actions.dismiss) { Text(stringResource(R.string.cancel)) }
                }
            })
        else -> Unit
    }
}

private fun failureText(reason: UpdateState.Reason) = when (reason) {
    UpdateState.Reason.NETWORK -> R.string.update_failed_network
    UpdateState.Reason.DOWNLOAD -> R.string.update_failed_download
    UpdateState.Reason.INVALID_APK -> R.string.update_failed_apk
}

@Composable private fun ReleaseSummary(release: ReleaseInfo, installed: Long) {
    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(release.title, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (release.prerelease) Surface(color = ionColors().warning, contentColor = ionColors().onWarning, shape = RoundedCornerShape(50)) {
                Text(stringResource(R.string.update_prerelease), style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
            Text(stringResource(R.string.update_installed_build, installed.toInt()), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val notes = remember(release.notes) { Updates.shortNotes(release.notes) }
        if (notes.isNotBlank()) Text(notes, style = MaterialTheme.typography.bodyMedium)
    }
}

/** App → Updates: installed version, channel, launch check and a manual check with its result. */
@Composable internal fun UpdatesScreen(ui: UpdateUi, actions: UpdateActions, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "heading", contentType = "heading") { DetailHeading(stringResource(R.string.updates_title), stringResource(R.string.updates_hint), onBack) }
        item(key = "installed", contentType = "status") { CheckCard(ui, actions) }
        item(key = "channel-title", contentType = "section") { SectionHeader(stringResource(R.string.updates_channel)) }
        item(key = "channel", contentType = "group") {
            GroupColumn {
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ChannelRow(UpdateChannel.STABLE, R.string.updates_channel_stable, R.string.updates_channel_stable_hint, Icons.Rounded.Verified, ui, actions)
                    ChannelRow(UpdateChannel.PRERELEASE, R.string.updates_channel_pre, R.string.updates_channel_pre_hint, Icons.Rounded.Science, ui, actions)
                }
            }
        }
        item(key = "auto", contentType = "group") {
            GroupColumn {
                SwitchRow(Icons.Rounded.Autorenew, stringResource(R.string.updates_auto), ui.config.autoCheck,
                    stringResource(R.string.updates_auto_hint), true) { actions.setConfig(ui.config.copy(autoCheck = it)) }
            }
        }
        item(key = "releases", contentType = "link") {
            ActionRow(stringResource(R.string.updates_releases), Updates.RELEASES_PAGE.removePrefix("https://"),
                Icons.AutoMirrored.Rounded.OpenInNew) { actions.openPage(null) }
        }
    }
}

@Composable private fun CheckCard(ui: UpdateUi, actions: UpdateActions) {
    val ion = ionColors()
    Surface(color = ion.hero, contentColor = ion.onHero, shape = IonShapes.hero) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.updates_installed, ui.version, ui.build.toInt()), style = MaterialTheme.typography.titleLarge)
            val checking = ui.state is UpdateState.Checking
            val status = when (val s = ui.state) {
                UpdateState.Checking -> stringResource(R.string.updates_checking)
                UpdateState.UpToDate -> stringResource(R.string.updates_up_to_date)
                is UpdateState.Failed -> stringResource(failureText(s.reason))
                is UpdateState.Available -> stringResource(R.string.updates_found, s.release.build)
                else -> if (ui.checkedAt > 0) stringResource(R.string.updates_last_checked,
                    remember(ui.checkedAt) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ui.checkedAt)) })
                    else stringResource(R.string.updates_never)
            }
            Text(status, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = actions.check, enabled = !checking && ui.state !is UpdateState.Downloading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ion.accent, contentColor = ion.onAccent)) {
                if (checking) LoadingIndicator(Modifier.size(22.dp), color = ion.onAccent) else Icon(Icons.Rounded.Refresh, null, Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.updates_check_now))
            }
        }
    }
}

@Composable private fun ChannelRow(channel: UpdateChannel, title: Int, hint: Int, icon: androidx.compose.ui.graphics.vector.ImageVector,
    ui: UpdateUi, actions: UpdateActions) {
    val selected = ui.config.channel == channel
    Surface(color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(IonShapes.xs)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 76.dp)
            .selectable(selected = selected, role = Role.RadioButton) { actions.setConfig(ui.config.copy(channel = channel)) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}
