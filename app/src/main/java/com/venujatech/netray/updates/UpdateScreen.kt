package com.venujatech.netray.updates

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.venujatech.netray.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    installedVersion: String,
    result: UpdateCheckResult?,
    checking: Boolean,
    downloadState: UpdateDownloadState,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onUpdate: (AndroidRelease) -> Unit,
    onCancelDownload: () -> Unit,
    onRetryInstall: (UpdateDownloadState.Ready) -> Unit
) {
    val context = LocalContext.current
    var pendingInstall by remember { mutableStateOf<UpdateDownloadState.Ready?>(null) }
    var showInstallPermissionDialog by remember { mutableStateOf(false) }
    val installSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val ready = pendingInstall
        pendingInstall = null
        if (ready != null && context.packageManager.canRequestPackageInstalls()) onRetryInstall(ready)
        else if (ready != null) Toast.makeText(context, R.string.update_install_permission_denied, Toast.LENGTH_LONG).show()
    }
    val requestInstall: (UpdateDownloadState.Ready) -> Unit = { ready ->
        if (context.packageManager.canRequestPackageInstalls()) onRetryInstall(ready)
        else {
            pendingInstall = ready
            showInstallPermissionDialog = true
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.update_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_lucide_x), contentDescription = stringResource(R.string.action_close))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.update_current_version, installedVersion), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onCheck, enabled = !checking, modifier = Modifier.fillMaxWidth()) {
                if (checking) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.update_checking))
                } else Text(stringResource(R.string.update_check))
            }

            when (val status = result) {
                is UpdateCheckResult.Current -> Card(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.update_up_to_date, status.version), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurface)
                }
                is UpdateCheckResult.Failed -> Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(status.message, color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = onCheck) { Text(stringResource(R.string.action_retry)) }
                    }
                }
                is UpdateCheckResult.Available -> UpdateReleasePanel(status.release, onUpdate)
                null -> Unit
            }

            when (val download = downloadState) {
                is UpdateDownloadState.Downloading -> {
                    val fraction = if (download.total > 0) (download.downloaded.toFloat() / download.total).coerceIn(0f, 1f) else 0f
                    val percent = (fraction * 100).toInt()
                    val downloadedMb = download.downloaded / 1_000_000f
                    val totalMb = download.total / 1_000_000f
                    Text(stringResource(R.string.update_download_progress, percent, "%.1f".format(downloadedMb), "%.1f".format(totalMb)))
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = onCancelDownload, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_cancel_download)) }
                }
                is UpdateDownloadState.Ready -> OutlinedButton(onClick = { requestInstall(download) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.update_install_retry))
                }
                is UpdateDownloadState.Failed -> {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(download.message, color = MaterialTheme.colorScheme.onErrorContainer)
                            download.release?.let { release ->
                                OutlinedButton(onClick = { onUpdate(release) }) { Text(stringResource(R.string.action_retry)) }
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
    }
    if (showInstallPermissionDialog) AlertDialog(
        onDismissRequest = { showInstallPermissionDialog = false; pendingInstall = null },
        title = { Text(stringResource(R.string.update_install_permission_title)) },
        text = { Text(stringResource(R.string.update_install_permission_message)) },
        confirmButton = {
            TextButton(onClick = {
                showInstallPermissionDialog = false
                runCatching {
                    installSettings.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                }.onFailure {
                    pendingInstall = null
                    Toast.makeText(context, R.string.update_install_permission_denied, Toast.LENGTH_LONG).show()
                }
            }) { Text(stringResource(R.string.update_install_permission_settings)) }
        },
        dismissButton = {
            TextButton(onClick = { showInstallPermissionDialog = false; pendingInstall = null }) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun UpdateReleasePanel(release: AndroidRelease, onUpdate: (AndroidRelease) -> Unit) {
    val date = release.publishedAt.take(10).ifBlank { "—" }
    val size = if (release.assetSize > 0) "%.1f MB".format(release.assetSize / 1_000_000f) else "—"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.update_available), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.update_version, release.version), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.update_release_date, date), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.update_apk_size, size), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { onUpdate(release) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.update_button))
            }
            Column(modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReleaseNotes(release.notes)
            }
        }
    }
}

@Composable
private fun ReleaseNotes(markdown: String) {
    val context = LocalContext.current
    val safe = markdown.take(MAX_NOTES_CHARS)
        .replace(Regex("(?is)<[^>]*>"), "")
        .replace(Regex("!\\[[^]]*]\\([^)]*\\)"), "")
    val linkPattern = Regex("https://[^\\s)<>]+")
    safe.lines().forEach { raw ->
        val line = raw.trim()
        if (line.isBlank()) {
            Spacer(Modifier.height(2.dp))
        } else {
            val heading = line.startsWith("#")
            val bullet = line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ")
            val text = line.trimStart('#', ' ', '-', '*', '+').replace("**", "").replace("__", "").replace("`", "")
            Text(
                text = text,
                style = if (heading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = if (heading) FontWeight.SemiBold else FontWeight.Normal,
                modifier = if (bullet) Modifier.padding(start = 12.dp) else Modifier
            )
            linkPattern.findAll(text).forEach { match ->
                val safeUrl = runCatching { match.value.toUri() }.getOrNull()?.takeIf { it.scheme == "https" && it.userInfo == null }
                if (safeUrl != null) {
                    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, safeUrl)) } }) {
                        Text(match.value, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private const val MAX_NOTES_CHARS = 100_000
