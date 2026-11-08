package com.venujatech.netray.whitelist

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.Bundle
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.venujatech.netray.R
import com.venujatech.netray.proxy.NetrayProxyService
import com.venujatech.netray.ui.theme.NetrayTheme
import com.venujatech.netray.ui.theme.paletteForAccent
import com.venujatech.netray.vpn.NetrayVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppWhitelistActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val theme = preferences.getString("theme_mode", "system") ?: "system"
        val dark = when (theme) {
            "dark" -> true
            "light" -> false
            else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        val palette = paletteForAccent(preferences.getString("accent_color", "blue") ?: "blue", dark)
        setContent {
            NetrayTheme(themeMode = theme, dynamicColor = false, palette = palette) {
                AppWhitelistScreen(onBack = ::finish)
            }
        }
    }
}

private data class InstalledApp(val label: String, val packageName: String, val system: Boolean)

private object AppIconCache {
    val icons = object : LruCache<String, Bitmap>(128) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppWhitelistScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { AppWhitelistRepository.get(context) }
    val whitelist by repository.whitelist.collectAsStateWithLifecycle()
    val vpnConnected by NetrayVpnService.state.collectAsStateWithLifecycle()
    val proxyState by NetrayProxyService.state.collectAsStateWithLifecycle()
    val connected = vpnConnected is NetrayVpnService.State.Connected || proxyState is NetrayProxyService.State.Connected
    var tab by rememberSaveable { mutableStateOf(0) }
    var urlInput by rememberSaveable { mutableStateOf("") }
    var urlSearch by rememberSaveable { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    var appError by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var pickerSearch by rememberSaveable { mutableStateOf("") }
    var showSystemApps by rememberSaveable { mutableStateOf(false) }
    var pickerSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loadingApps by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { queryInstalledApps(context) }
        loadingApps = false
    }
    LaunchedEffect(showPicker) {
        if (showPicker) pickerSelection = whitelist.appPackages.toSet()
    }

    val filteredRules = remember(whitelist.rules, urlSearch) {
        whitelist.rules.filter { it.value.contains(urlSearch.trim(), ignoreCase = true) }
    }
    val appByPackage = remember(apps) { apps.associateBy(InstalledApp::packageName) }
    val filteredApps = remember(apps, pickerSearch, showSystemApps) {
        apps.filter { (showSystemApps || !it.system) && (it.label.contains(pickerSearch, true) || it.packageName.contains(pickerSearch, true)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.whitelist_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_lucide_x), contentDescription = stringResource(R.string.action_close))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(tab == 0, { tab = 0 }, SegmentedButtonDefaults.itemShape(0, 2), label = { Text(stringResource(R.string.whitelist_urls_tab)) })
                SegmentedButton(tab == 1, { tab = 1 }, SegmentedButtonDefaults.itemShape(1, 2), label = { Text(stringResource(R.string.whitelist_apps_tab)) })
            }

            if (connected) {
                Text(stringResource(R.string.whitelist_reconnect), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.whitelist_lockdown_note), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            whitelist.loadError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            if (tab == 0) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it; addError = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.whitelist_url_hint)) },
                    singleLine = true,
                    isError = addError != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addUrl(repository, urlInput, { urlInput = "" }, { addError = it }) })
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = { addUrl(repository, urlInput, { urlInput = "" }, { addError = it }) }, enabled = urlInput.isNotBlank()) {
                        Text(stringResource(R.string.whitelist_add))
                    }
                }
                addError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(
                    value = urlSearch,
                    onValueChange = { urlSearch = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.whitelist_search)) },
                    singleLine = true
                )
                if (whitelist.rules.isEmpty()) {
                    Text(stringResource(R.string.whitelist_empty_urls), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filteredRules, key = WhitelistRule::matcher) { rule ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(rule.value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = { repository.removeRule(rule) }) {
                                    Icon(painterResource(R.drawable.ic_lucide_trash_2), contentDescription = stringResource(R.string.whitelist_remove))
                                }
                            }
                        }
                    }
                }
                Text(stringResource(R.string.whitelist_url_behavior), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(stringResource(R.string.whitelist_app_explanation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                appError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.whitelist_select_app))
                }
                if (whitelist.appPackages.isEmpty()) Text(stringResource(R.string.whitelist_empty_apps), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(whitelist.appPackages, key = { it }) { packageName ->
                        val app = appByPackage[packageName]
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                PackageIcon(packageName, app?.label ?: packageName)
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(app?.label ?: stringResource(R.string.whitelist_missing_app), fontWeight = FontWeight.Medium)
                                    Text(packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (app == null) Text(stringResource(R.string.whitelist_app_missing), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = { repository.removeApp(packageName) }) {
                                    Icon(painterResource(R.drawable.ic_lucide_trash_2), contentDescription = stringResource(R.string.whitelist_remove))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPicker) AlertDialog(
        onDismissRequest = { showPicker = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(stringResource(R.string.whitelist_picker_title)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(pickerSearch, { pickerSearch = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.whitelist_search)) }, singleLine = true)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.whitelist_show_system_apps), Modifier.weight(1f))
                    Switch(showSystemApps, { showSystemApps = it })
                }
                if (loadingApps) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else LazyColumn(Modifier.weight(1f)) {
                    items(filteredApps, key = InstalledApp::packageName) { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                pickerSelection = if (app.packageName in pickerSelection) pickerSelection - app.packageName else pickerSelection + app.packageName
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PackageIcon(app.packageName, app.label)
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(app.label, maxLines = 1)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Checkbox(checked = app.packageName in pickerSelection, onCheckedChange = {
                                pickerSelection = if (it) pickerSelection + app.packageName else pickerSelection - app.packageName
                            })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching { repository.setApps(pickerSelection) }
                    .onSuccess { appError = null; showPicker = false }
                    .onFailure { appError = it.message ?: "Could not save app whitelist." }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) } }
    )
}

private fun addUrl(repository: AppWhitelistRepository, value: String, clear: () -> Unit, fail: (String?) -> Unit) {
    try {
        repository.addRule(value)
        clear()
        fail(null)
    } catch (error: Exception) {
        fail(error.message ?: "Invalid whitelist entry.")
    }
}

private fun queryInstalledApps(context: Context): List<InstalledApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return context.packageManager.queryIntentActivities(intent, 0).mapNotNull { resolveInfo ->
        val activity = resolveInfo.activityInfo ?: return@mapNotNull null
        val app = activity.applicationInfo
        if (app.packageName == context.packageName) return@mapNotNull null
        InstalledApp(
            label = resolveInfo.loadLabel(context.packageManager).toString().ifBlank { app.packageName },
            packageName = app.packageName,
            system = app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        )
    }.distinctBy(InstalledApp::packageName).sortedBy { it.label.lowercase() }
}

@Composable
private fun PackageIcon(packageName: String, label: String) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf(AppIconCache.icons.get(packageName)) }
    LaunchedEffect(packageName) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager.getApplicationIcon(packageName).toBitmap(48, 48, Bitmap.Config.ARGB_8888)
                }.getOrNull()?.also { AppIconCache.icons.put(packageName, it) }
            }
        }
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = null, Modifier.size(40.dp).clip(CircleShape))
    else Box(Modifier.size(40.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
        Text(label.firstOrNull()?.uppercase() ?: "?", color = MaterialTheme.colorScheme.primary)
    }
}
