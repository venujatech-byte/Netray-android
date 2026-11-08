package com.venujatech.netray

import android.app.Activity
import android.graphics.Bitmap
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.webkit.WebView
import android.widget.Toast
import com.venujatech.netray.updates.UpdateCheckResult
import com.venujatech.netray.updates.GitHubReleaseRepository
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.venujatech.netray.profiles.Profile
import com.venujatech.netray.profiles.ProfileStore
import com.venujatech.netray.profiles.NativeBackend
import com.venujatech.netray.profiles.ConfigDraft
import com.venujatech.netray.profiles.ConfigFieldKeys
import com.venujatech.netray.profiles.ConfigParserRouter
import com.venujatech.netray.profiles.ProtocolKind
import com.venujatech.netray.proxy.NetrayProxyService
import com.venujatech.netray.proxy.ProxyConfig
import com.venujatech.netray.ui.AboutScreen
import com.venujatech.netray.ui.CheckIpScreen
import com.venujatech.netray.ui.ProfileFormScreen
import com.venujatech.netray.ui.RawConfigDialog
import com.venujatech.netray.ui.RawJsonEditorScreen
import com.venujatech.netray.ui.LogsScreen
import com.venujatech.netray.ui.QrScannerScreen
import com.venujatech.netray.ui.QrImageDecoder
import com.venujatech.netray.ui.SettingsScreen
import com.venujatech.netray.ui.theme.NetrayTheme
import com.venujatech.netray.ui.theme.LocalNetrayPalette
import com.venujatech.netray.ui.theme.colorForAccent
import com.venujatech.netray.ui.theme.paletteForAccent
import com.venujatech.netray.ui.theme.darkColorSchemeWithAccent
import com.venujatech.netray.ui.theme.lightColorSchemeWithAccent
import com.venujatech.netray.vpn.NetrayVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

fun colorSchemeForAccent(accentColor: String, isDark: Boolean): ColorScheme {
    val accent = colorForAccent(accentColor)
    return if (isDark) {
        darkColorSchemeWithAccent(accent)
    } else {
        lightColorSchemeWithAccent(accent)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("settings", MODE_PRIVATE)
        val mode = preferences.getString("theme_mode", "system") ?: "system"
        val dark = when (mode) {
            "dark" -> true
            "light" -> false
            else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        val startupPalette = paletteForAccent(preferences.getString("accent_color", "blue") ?: "blue", dark)
        window.decorView.setBackgroundColor(startupPalette.background.toArgb())
        window.statusBarColor = startupPalette.background.toArgb()
        window.navigationBarColor = startupPalette.background.toArgb()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        setContent { NetrayScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetrayScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ProfileStore(context) }
    val profiles = remember { mutableStateListOf<Profile>() }
    val pingResults = remember { mutableStateMapOf<String, String>() }
    val pinging = remember { mutableStateMapOf<String, Boolean>() }
    var selectedId by rememberSaveable { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf("") }
    var importError by rememberSaveable { mutableStateOf("") }
    var storageError by remember { mutableStateOf("") }
    var storeReady by remember { mutableStateOf(false) }
    var notificationError by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Profile?>(null) }
    var editTarget by remember { mutableStateOf<Profile?>(null) }
    var editError by rememberSaveable { mutableStateOf("") }
    var editSaving by remember { mutableStateOf(false) }
    var shareTarget by remember { mutableStateOf<Profile?>(null) }
    var shareLink by remember { mutableStateOf("") }
    var shareCopied by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var addMenuExpanded by remember { mutableStateOf(false) }
    var qrSourceMenuExpanded by remember { mutableStateOf(false) }
    var importPrefillClipboard by remember { mutableStateOf(true) }
    var importInProgress by remember { mutableStateOf(false) }
    var addProtocolKey by rememberSaveable { mutableStateOf<String?>(null) }
    var importedDraft by remember { mutableStateOf<ConfigDraft?>(null) }
    var rawDialogVisible by remember { mutableStateOf(false) }
    var rawEditorVisible by rememberSaveable { mutableStateOf(false) }
    var rawRemarks by rememberSaveable { mutableStateOf("") }
    var rawJson by rememberSaveable { mutableStateOf("") }
    var rawProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var rawEditorError by rememberSaveable { mutableStateOf("") }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    val settings = remember { context.getSharedPreferences("settings", 0) }
    var themeMode by rememberSaveable { mutableStateOf(settings.getString("theme_mode", "system") ?: "system") }
    var allowInsecureTls by remember { mutableStateOf(settings.getBoolean("allow_insecure_tls", false)) }
    var runAtStartup by remember { mutableStateOf(settings.getBoolean("run_at_startup", false)) }
    var connectAtStartup by remember { mutableStateOf(settings.getBoolean("connect_at_startup", false)) }
    var autoReconnect by remember { mutableStateOf(settings.getBoolean("auto_reconnect", true)) }
    var notifyConnectionDrops by remember { mutableStateOf(settings.getBoolean("notify_connection_drops", true)) }
    var autoCheckUpdates by remember { mutableStateOf(settings.getBoolean("auto_check_updates", true)) }
    var instantImport by remember { mutableStateOf(settings.getBoolean("instant_import", false)) }
    var socksPort by remember { mutableIntStateOf(settings.getInt("proxy_socks_port", 10808)) }
    var httpPort by remember { mutableIntStateOf(settings.getInt("proxy_http_port", 10809)) }
    var accentColor by remember { mutableStateOf(settings.getString("accent_color", "blue") ?: "blue") }
    val state by NetrayVpnService.state.collectAsStateWithLifecycle()
    val proxyState by NetrayProxyService.state.collectAsStateWithLifecycle()
    val vpn = state is NetrayVpnService.State.Connected
    val proxyConnected = proxyState is NetrayProxyService.State.Connected
    val connected = vpn || proxyConnected
    val busy = state is NetrayVpnService.State.Connecting || state is NetrayVpnService.State.Stopping
    val proxyBusy = proxyState is NetrayProxyService.State.Connecting || proxyState is NetrayProxyService.State.Stopping
    val anyBusy = busy || proxyBusy
    val selectedProfile = profiles.firstOrNull { it.id == selectedId }
    var activeMode by rememberSaveable { mutableIntStateOf(0) } // 0=TUN, 1=Proxy
    var proxyHttpPort by remember { mutableIntStateOf(ProxyConfig.DEFAULT_HTTP_PORT) }

    var currentScreen by rememberSaveable { mutableStateOf("configs") }
    var selectedMode by rememberSaveable { mutableIntStateOf(settings.getInt("connection_mode", 0)) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val importFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    BackHandler(enabled = drawerState.isOpen || currentScreen != "configs" || editTarget != null || addProtocolKey != null || rawEditorVisible || rawDialogVisible) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (rawEditorVisible) {
            rawEditorVisible = false
            rawDialogVisible = true
        } else if (rawDialogVisible) {
            rawDialogVisible = false
            rawProfileId = null
        } else if (editTarget != null || addProtocolKey != null) {
            if (!editSaving) {
                editTarget = null
                addProtocolKey = null
                importedDraft = null
                editError = ""
            }
        } else {
            currentScreen = "configs"
        }
    }

    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { store.load() } }
            .onSuccess { loaded ->
                profiles.addAll(loaded)
                if (selectedId.isBlank()) selectedId = loaded.firstOrNull()?.id.orEmpty()
                storeReady = true
            }
            .onFailure { storageError = "Saved profiles could not be read. They have been kept unchanged." }
    }

    val saveProfile: (Profile, String?) -> Unit = { candidate, replacingId ->
        scope.launch {
            if (!storeReady) {
                editError = "Saved profiles are unavailable; restart before changing them."
                rawEditorError = editError
                return@launch
            }
            val duplicate = profiles.firstOrNull { it.id != replacingId && it.duplicateKey == candidate.duplicateKey }
            if (duplicate != null) {
                editError = context.getString(R.string.error_duplicate_config)
                rawEditorError = editError
                return@launch
            }
            editSaving = true
            editError = ""
            try {
                val next = if (replacingId == null) profiles + candidate
                else profiles.map { if (it.id == replacingId) candidate.copy(id = replacingId) else it }
                withContext(Dispatchers.IO) { store.save(next) }
                profiles.clear()
                profiles.addAll(next)
                selectedId = candidate.id
                replacingId?.let { pingResults.remove(it); pinging.remove(it) }
                editTarget = null
                addProtocolKey = null
                importedDraft = null
                rawDialogVisible = false
                rawEditorVisible = false
                rawProfileId = null
                rawEditorError = ""
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val message = "Could not securely save profile; the previous saved data was preserved."
                editError = message
                rawEditorError = message
            } finally {
                editSaving = false
            }
        }
    }

    val openDraft: (ConfigDraft) -> Unit = { draft ->
        editError = ""
        if (draft.protocol == ProtocolKind.RAW_JSON) {
            rawProfileId = null
            rawRemarks = draft[ConfigFieldKeys.REMARKS]
            rawJson = draft[ConfigFieldKeys.RAW_JSON]
            rawEditorError = ""
            rawDialogVisible = true
        } else {
            editTarget = null
            addProtocolKey = draft.protocol.key
            importedDraft = draft
            if (draft.protocol == ProtocolKind.SSH && !settings.getBoolean("ssh_help_toast_shown", false)) {
                Toast.makeText(context, R.string.ssh_help_toast, Toast.LENGTH_LONG).show()
                settings.edit().putBoolean("ssh_help_toast_shown", true).apply()
            }
        }
    }

    val openProtocol: (ProtocolKind) -> Unit = { protocol ->
        addMenuExpanded = false
        importedDraft = null
        if (protocol == ProtocolKind.RAW_JSON) {
            rawProfileId = null
            rawRemarks = ""
            rawJson = ""
            rawEditorError = ""
            rawDialogVisible = true
        } else {
            editTarget = null
            addProtocolKey = protocol.key
            if (protocol == ProtocolKind.SSH && !settings.getBoolean("ssh_help_toast_shown", false)) {
                Toast.makeText(context, R.string.ssh_help_toast, Toast.LENGTH_LONG).show()
                settings.edit().putBoolean("ssh_help_toast_shown", true).apply()
            }
        }
    }

    fun processImportText(
        importText: String,
        fromQr: Boolean = false,
        onError: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() },
        onAccepted: () -> Unit = {}
    ) {
        scope.launch {
            val batch = withContext(Dispatchers.Default) { ConfigParserRouter.parseMany(importText) }
            if (batch.configs.isEmpty()) {
                val message = if (fromQr) context.getString(R.string.qr_unsupported_config)
                else batch.errors.firstOrNull() ?: context.getString(R.string.error_empty_import)
                onError(message)
            } else if (batch.configs.size == 1) {
                val draft = batch.configs.single()
                runCatching { Profile.fromDraft(draft) }
                    .onFailure { onError(it.message ?: context.getString(R.string.error_invalid_config, draft.protocol.key)) }
                    .onSuccess { profile ->
                        if (profiles.any { it.duplicateKey == profile.duplicateKey }) {
                            onError(context.getString(R.string.error_duplicate_config))
                        } else {
                            openDraft(draft)
                            onAccepted()
                            if (batch.skipped > 0) Toast.makeText(context, context.getString(R.string.import_summary, 0, batch.skipped), Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                if (!storeReady) {
                    onError("Saved profiles are unavailable; restart before importing.")
                    return@launch
                }
                val keys = profiles.mapTo(mutableSetOf()) { it.duplicateKey }
                val additions = mutableListOf<Profile>()
                var skipped = batch.skipped
                var validParsed = 0
                batch.configs.forEach { draft ->
                    val profile = runCatching { Profile.fromDraft(draft) }.getOrNull()
                    if (profile == null) skipped++
                    else {
                        validParsed++
                        if (!keys.add(profile.duplicateKey)) skipped++ else additions += profile
                    }
                }
                if (validParsed == 0) {
                    onError(batch.errors.firstOrNull() ?: context.getString(R.string.error_no_supported_config))
                    return@launch
                }
                if (additions.isNotEmpty() && storeReady) {
                    try {
                        val next = profiles + additions
                        withContext(Dispatchers.IO) { store.save(next) }
                        profiles.addAll(additions)
                        selectedId = additions.first().id
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        onError("Could not securely save imported profiles; existing profiles were preserved.")
                        return@launch
                    }
                } else if (additions.isNotEmpty()) {
                    skipped += additions.size
                }
                val importedCount = additions.size
                Toast.makeText(context, context.getString(R.string.import_summary, importedCount, skipped), Toast.LENGTH_LONG).show()
                onAccepted()
            }
        }
    }

    val importConfigText: (String, Boolean) -> Unit = { text, fromQr -> processImportText(text, fromQr) }

    LaunchedEffect(showImport, importPrefillClipboard) {
        if (showImport) {
            if (importPrefillClipboard && link.isBlank()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                link = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
            }
            importFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    var pendingProfileId by rememberSaveable { mutableStateOf("") }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingProfileId.isNotBlank()) startTunnel(context, pendingProfileId, selectedMode)
        pendingProfileId = ""
    }
    val requestVpnConsent: () -> Unit = {
        // Proxy mode never touches VpnService API.
        if (selectedMode == 1) {
            startProxyTunnel(context, selectedId, selectedMode)
        } else {
            val prepare = VpnService.prepare(context)
            if (prepare == null) startTunnel(context, selectedId, selectedMode)
            else { pendingProfileId = selectedId; consent.launch(prepare) }
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) requestVpnConsent()
        else notificationError = "Allow notifications so Android can show the VPN's background status."
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                if (context.contentResolver.getType(uri)?.startsWith("image/") == true) readQrFromImage(context, uri)
                else readImportFile(context, uri)
            }.onSuccess { importConfigText(it, false) }
                .onFailure { Toast.makeText(context, it.message ?: context.getString(R.string.error_empty_import), Toast.LENGTH_LONG).show() }
        }
    }
    val qrImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            runCatching { readQrFromImage(context, uri) }
                .onSuccess { importConfigText(it, true) }
                .onFailure { Toast.makeText(context, it.message ?: context.getString(R.string.error_empty_import), Toast.LENGTH_LONG).show() }
        }
    }

    val isDark = themeMode == "dark" || (themeMode == "system" && androidx.compose.foundation.isSystemInDarkTheme())
    val palette = paletteForAccent(accentColor, isDark)
    val colorScheme = colorSchemeForAccent(accentColor, isDark)
    NetrayTheme(themeMode = themeMode, dynamicColor = false, colorScheme = colorScheme, palette = palette) {
        androidx.compose.runtime.SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = palette.background.toArgb()
            window.navigationBarColor = palette.background.toArgb()
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
        if (rawEditorVisible) {
            RawJsonEditorScreen(
                initialJson = rawJson,
                error = rawEditorError,
                onCancel = { rawEditorVisible = false; rawDialogVisible = true },
                onPaste = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    rawJson = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                },
                onSave = { json ->
                    val draft = ConfigDraft.create(ProtocolKind.RAW_JSON).withAll(mapOf(
                        ConfigFieldKeys.REMARKS to rawRemarks,
                        ConfigFieldKeys.RAW_JSON to json
                    ))
                    runCatching { Profile.fromDraft(draft, rawProfileId ?: java.util.UUID.randomUUID().toString()) }
                        .onSuccess { saveProfile(it, rawProfileId) }
                        .onFailure { rawEditorError = it.message ?: context.getString(R.string.error_invalid_json) }
                }
            )
        } else if (editTarget != null) {
            val original = editTarget!!
            ProfileFormScreen(
                protocol = original.protocol,
                existing = original,
                saving = editSaving,
                saveError = editError,
                onDiscard = { if (!editSaving) { editTarget = null; editError = "" } },
                onSave = { candidate -> saveProfile(candidate, original.id) }
            )
        } else if (addProtocolKey != null) {
            val protocol = ProtocolKind.fromKey(addProtocolKey!!) ?: ProtocolKind.VLESS
            ProfileFormScreen(
                protocol = protocol,
                existing = null,
                initialDraft = importedDraft,
                saving = editSaving,
                saveError = editError,
                onDiscard = { if (!editSaving) { addProtocolKey = null; importedDraft = null; editError = "" } },
                onSave = { candidate -> saveProfile(candidate, null) }
            )
        } else if (showQrScanner) {
            QrScannerScreen(
                onScanResult = { raw -> showQrScanner = false; importConfigText(raw, true) },
                onImageScanResults = { raw -> showQrScanner = false; importConfigText(raw.joinToString("\n"), true) },
                onClose = { showQrScanner = false }
            )
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(R.drawable.netray_logo),
                            contentDescription = "Netray logo",
                            modifier = Modifier.size(52.dp).clip(CircleShape)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Netray", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.app_version_short, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                    NavigationDrawerItem(
                        icon = { Icon(painterResource(R.drawable.ic_lucide_server), contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("Configs") },
                        selected = currentScreen == "configs",
                        onClick = { currentScreen = "configs"; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(painterResource(R.drawable.ic_lucide_file_text), contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("Logs") },
                        selected = currentScreen == "logs",
                        onClick = { currentScreen = "logs"; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(painterResource(R.drawable.ic_lucide_globe), contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("Check IP") },
                        selected = currentScreen == "check_ip",
                        onClick = { currentScreen = "check_ip"; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(painterResource(R.drawable.ic_lucide_settings), contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("Settings") },
                        selected = currentScreen == "settings",
                        onClick = { currentScreen = "settings"; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(painterResource(R.drawable.ic_lucide_info), contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("About") },
                        selected = currentScreen == "about",
                        onClick = { currentScreen = "about"; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        ) {
            when (currentScreen) {
                "logs" -> LogsScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onBack = { currentScreen = "configs" }
                )
                "check_ip" -> CheckIpScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    vpnConnected = vpn
                )
                "settings" -> SettingsScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    settings = settings,
                    appVersion = BuildConfig.VERSION_NAME,
                    themeMode = themeMode,
                    onThemeChange = { mode ->
                        themeMode = mode
                        settings.edit().putString("theme_mode", mode).apply()
                    },
                    connectionMode = selectedMode,
                    onConnectionModeChange = { mode ->
                        selectedMode = mode
                        settings.edit().putInt("connection_mode", mode).apply()
                    },
                    allowInsecureTls = allowInsecureTls,
                    onAllowInsecureTlsChange = { checked ->
                        allowInsecureTls = checked
                        settings.edit().putBoolean("allow_insecure_tls", checked).apply()
                    },
                    runAtStartup = runAtStartup,
                    onRunAtStartupChange = { checked ->
                        runAtStartup = checked
                        settings.edit().putBoolean("run_at_startup", checked).apply()
                    },
                    connectAtStartup = connectAtStartup,
                    onConnectAtStartupChange = { checked ->
                        connectAtStartup = checked
                        settings.edit().putBoolean("connect_at_startup", checked).apply()
                    },
                    autoReconnect = autoReconnect,
                    onAutoReconnectChange = { checked ->
                        autoReconnect = checked
                        settings.edit().putBoolean("auto_reconnect", checked).apply()
                    },
                    notifyConnectionDrops = notifyConnectionDrops,
                    onNotifyConnectionDropsChange = { checked ->
                        notifyConnectionDrops = checked
                        settings.edit().putBoolean("notify_connection_drops", checked).apply()
                    },
                    autoCheckUpdates = autoCheckUpdates,
                    onAutoCheckUpdatesChange = { checked ->
                        autoCheckUpdates = checked
                        settings.edit().putBoolean("auto_check_updates", checked).apply()
                    },
                    instantImport = instantImport,
                    onInstantImportChange = { checked ->
                        instantImport = checked
                        settings.edit().putBoolean("instant_import", checked).apply()
                    },
                    socksPort = socksPort,
                    onSocksPortChange = { port ->
                        socksPort = port
                        settings.edit().putInt("proxy_socks_port", port).apply()
                    },
                    httpPort = httpPort,
                    onHttpPortChange = { port ->
                        httpPort = port
                        settings.edit().putInt("proxy_http_port", port).apply()
                    },
                    accentColor = accentColor,
                    onAccentColorChange = { checked ->
                        accentColor = checked
                        settings.edit().putString("accent_color", checked).apply()
                    },
                    vpnBusy = anyBusy,
                    connected = connected,
                    onCheckForUpdates = { scope.launch {
                        val repository = GitHubReleaseRepository(context)
                        val result = withContext(Dispatchers.IO) { repository.check(BuildConfig.VERSION_NAME, false) }
                        when (result) {
                            is UpdateCheckResult.Available -> {
                                Toast.makeText(context, "Update available: ${result.release.version}", Toast.LENGTH_LONG).show()
                            }
                            is UpdateCheckResult.Current -> {
                                Toast.makeText(context, "You're up to date (v${result.version})", Toast.LENGTH_LONG).show()
                            }
                            is UpdateCheckResult.Failed -> {
                                Toast.makeText(context, "Failed to check updates: ${result.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }},
                    onViewLogs = { currentScreen = "logs" }
                )
                "about" -> AboutScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    appVersion = BuildConfig.VERSION_NAME
                )
                else -> {
                    var swipeDrag by remember { mutableFloatStateOf(0f) }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                                .padding(horizontal = 18.dp)
                                .draggable(
                                    orientation = Orientation.Horizontal,
                                    state = rememberDraggableState { delta -> swipeDrag += delta },
                                    onDragStopped = { velocity ->
                                        if (swipeDrag > 60f || velocity > 300f) {
                                            scope.launch { drawerState.open() }
                                        } else if (swipeDrag < -60f || velocity < -300f) {
                                            currentScreen = "logs"
                                        }
                                        swipeDrag = 0f
                                    }
                                )
                        ) {
                            Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_lucide_menu),
                                        contentDescription = "Open navigation drawer",
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Image(
                                    painter = painterResource(R.drawable.netray_logo),
                                    contentDescription = "Netray logo",
                                    modifier = Modifier.size(34.dp).clip(CircleShape)
                                )
                                Text("Netray", Modifier.padding(start = 12.dp).weight(1f), fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                                Box {
                                    IconButton(
                                        onClick = { addMenuExpanded = true },
                                        enabled = storeReady,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_lucide_plus),
                                            contentDescription = stringResource(R.string.action_add_profile),
                                            tint = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = addMenuExpanded,
                                        onDismissRequest = { addMenuExpanded = false },
                                        modifier = Modifier.width(280.dp),
                                        containerColor = palette.menuSurface
                                    ) {
                                        ProtocolKind.menuOrder.forEach { protocol ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                        Text(stringResource(protocol.titleRes), color = palette.textPrimary)
                                                        Spacer(Modifier.weight(1f))
                                                        ProtocolBadge(protocol.badge)
                                                    }
                                                },
                                                onClick = { openProtocol(protocol) },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                                Box {
                                    IconButton(
                                        onClick = { qrSourceMenuExpanded = true },
                                        enabled = storeReady,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_lucide_scan_line),
                                            contentDescription = stringResource(R.string.action_scan_qr),
                                            tint = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    DropdownMenu(expanded = qrSourceMenuExpanded, onDismissRequest = { qrSourceMenuExpanded = false }) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_scan_qr)) },
                                            onClick = { qrSourceMenuExpanded = false; showQrScanner = true }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_scan_qr_image)) },
                                            onClick = { qrSourceMenuExpanded = false; qrImagePicker.launch("image/*") }
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        importPrefillClipboard = true
                                        importError = ""
                                        showImport = true
                                    },
                                    enabled = storeReady,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_lucide_clipboard_paste),
                                        contentDescription = stringResource(R.string.action_import_clipboard),
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { filePicker.launch(arrayOf("*/*")) },
                                    enabled = storeReady,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_lucide_file_text),
                                        contentDescription = stringResource(R.string.action_import_file),
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Box {
                                    IconButton(
                                        onClick = { menuExpanded = true },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_lucide_more_vertical),
                                            contentDescription = "More options",
                                            tint = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_import_link)) },
                                            onClick = {
                                                menuExpanded = false
                                                importPrefillClipboard = false
                                                link = ""
                                                importError = ""
                                                showImport = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_paste)) },
                                            onClick = {
                                                menuExpanded = false
                                                importPrefillClipboard = true
                                                importError = ""
                                                showImport = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Settings") },
                                            onClick = { menuExpanded = false; currentScreen = "settings" }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("About Netray") },
                                            onClick = { menuExpanded = false; currentScreen = "about" }
                                        )
                                    }
                                }
                            }

                            if (storageError.isNotEmpty()) Text(storageError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                                items(profiles, key = { it.id }) { profile ->
                                    ProfileCard(profile, selected = profile.id == selectedId, connected = connected, storeReady = storeReady,
                                        pingResult = pingResults[profile.id], pinging = pinging[profile.id] == true,
                                        onPing = {
                                            if (pinging[profile.id] != true) scope.launch {
                                                pinging[profile.id] = true
                                                pingResults.remove(profile.id)
                                                val result = withContext(Dispatchers.IO) {
                                                    if (proxyConnected) {
                                                        httpPingViaProxy(proxyHttpPort)
                                                    } else {
                                                        httpPing()
                                                    }
                                                }
                                                pingResults[profile.id] = result
                                                pinging[profile.id] = false
                                            }
                                        },
                                        onSelect = { selectedId = profile.id }, onDelete = { deleteTarget = profile },
                                        onEdit = {
                                            editError = ""
                                            if (profile.protocol == ProtocolKind.RAW_JSON) {
                                                rawProfileId = profile.id
                                                rawRemarks = profile.name
                                                rawJson = profile.link
                                                rawEditorError = ""
                                                rawDialogVisible = true
                                            } else editTarget = profile
                                        },
                                        onShare = {
                                            shareTarget = profile
                                            shareLink = profile.link
                                        })
                                }
                            }

                            // Proxy mode guidance: shown only when proxy is active
                            if (proxyConnected && activeMode == 1) {
                                Card(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(PaddingValues(start = 2.dp, top = 0.dp, end = 2.dp, bottom = 8.dp)),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                ) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_lucide_globe),
                                                contentDescription = "Proxy active",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "Proxy mode active",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                        Text(
                                            text = "Apps are NOT auto-proxied. Configure each app to use:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = "SOCKS5: 127.0.0.1:${ProxyConfig.DEFAULT_SOCKS_PORT}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("SOCKS5 proxy", "127.0.0.1:${ProxyConfig.DEFAULT_SOCKS_PORT}"))
                                                Toast.makeText(context, "SOCKS5 address copied", Toast.LENGTH_SHORT).show()
                                            }) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_lucide_copy),
                                                    contentDescription = "Copy SOCKS5 address",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = "HTTP:  127.0.0.1:${proxyHttpPort}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("HTTP proxy", "127.0.0.1:$proxyHttpPort"))
                                                Toast.makeText(context, "HTTP address copied", Toast.LENGTH_SHORT).show()
                                            }) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_lucide_copy),
                                                    contentDescription = "Copy HTTP address",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Set HTTP proxy in Wi-Fi settings for browsers; set SOCKS5 in Telegram and other apps. TUN mode covers the whole device; proxy mode does not.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Open Wi-Fi Settings")
                                        }
                                    }
                                }
                            }

                            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                ConnectionStatusLabel(
                                    state = state,
                                    proxyState = proxyState,
                                    profileId = selectedId,
                                    proxyHttpPort = proxyHttpPort,
                                    onClick = { currentScreen = "logs" },
                                    modifier = Modifier.weight(1f)
                                )
                                FilledIconButton(
                                    onClick = {
                                        if (connected) {
                                            // Disconnect whichever mode is active
                                            if (activeMode == 1) {
                                                startProxyTunnel(context, null, mode = 1, disconnect = true)
                                            } else {
                                                startTunnel(context, null, mode = 0, disconnect = true)
                                            }
                                            activeMode = 0
                                        } else if (selectedMode == 1) {
                                            // Connect in Proxy mode — never calls VpnService API
                                            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                                                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                activeMode = 1
                                                val prefs = context.getSharedPreferences("settings", 0)
                                                proxyHttpPort = ProxyConfig.loadPorts(prefs)[1]
                                                startProxyTunnel(context, selectedId, selectedMode)
                                            }
                                        } else {
                                            // Connect in TUN mode — requires VPN consent
                                            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                                                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                activeMode = 0
                                                requestVpnConsent()
                                            }
                                        }
                                    },
                                    enabled = !anyBusy && (connected || selectedProfile != null),
                                    modifier = Modifier.size(64.dp),
                                    shape = CircleShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = palette.onPrimary
                                    )
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(if (connected) R.drawable.ic_stop else R.drawable.ic_start),
                                            contentDescription = if (connected) "Disconnect" else "Connect",
                                            modifier = Modifier.size(28.dp)
                                        )
                                        if (anyBusy) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = palette.onPrimary,
                                                strokeWidth = 2.5.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (rawDialogVisible) RawConfigDialog(
        remarks = rawRemarks,
        onRemarksChange = { rawRemarks = it },
        error = rawEditorError,
        onOpenEditor = { rawDialogVisible = false; rawEditorVisible = true },
        onCancel = { rawDialogVisible = false; rawProfileId = null; rawEditorError = "" }
    )

    if (showImport) AlertDialog(
        onDismissRequest = { },
        title = { Text(stringResource(R.string.import_from_clipboard_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = instantImport,
                        onCheckedChange = { enabled ->
                            instantImport = enabled
                            settings.edit().putBoolean("instant_import", enabled).apply()
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.instant_import), color = MaterialTheme.colorScheme.onSurface)
                }
                TextField(
                    value = link,
                    onValueChange = { link = it; importError = "" },
                    modifier = Modifier.fillMaxWidth().height(106.dp).focusRequester(importFocusRequester),
                    label = { Text(stringResource(R.string.clipboard_config_hint)) },
                    minLines = 4,
                    maxLines = 5,
                    enabled = !importInProgress,
                    isError = importError.isNotBlank(),
                    supportingText = importError.takeIf(String::isNotBlank)?.let { ({ Text(it) }) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = palette.surfaceVariant,
                        unfocusedContainerColor = palette.surfaceVariant,
                        focusedIndicatorColor = palette.outline,
                        unfocusedIndicatorColor = palette.outline
                    )
                )
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { showImport = false; importInProgress = false; link = ""; importError = "" },
                    enabled = !importInProgress
                ) { Text(stringResource(R.string.action_cancel), color = palette.error) }
                Button(
                    onClick = {
                        val text = link.trim()
                        if (text.isNotEmpty() && !importInProgress) {
                            importInProgress = true
                            processImportText(
                                importText = text,
                                onError = { message -> importError = message; importInProgress = false },
                                onAccepted = { showImport = false; link = ""; importError = ""; importInProgress = false }
                            )
                        }
                    },
                    enabled = link.isNotBlank() && !importInProgress,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary, contentColor = palette.onPrimary),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    if (importInProgress) CircularProgressIndicator(Modifier.size(20.dp), color = palette.onPrimary, strokeWidth = 2.dp)
                    else Text(stringResource(R.string.action_import))
                }
            }
        },
        dismissButton = {},
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = true)
    )

    if (notificationError.isNotEmpty()) AlertDialog(
        onDismissRequest = { notificationError = "" },
        title = { Text("Notifications are disabled") },
        text = { Text(notificationError) },
        confirmButton = { TextButton(onClick = { notificationError = "" }) { Text("OK") } },
        containerColor = MaterialTheme.colorScheme.surface
    )

    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete profile?") },
            text = { Text("Delete ${profile.name} from this device?") },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    val next = profiles.filterNot { it.id == profile.id }
                    runCatching { withContext(Dispatchers.IO) { store.save(next) } }
                        .onSuccess {
                            profiles.remove(profile)
                            if (selectedId == profile.id) selectedId = next.firstOrNull()?.id.orEmpty()
                            deleteTarget = null
                        }
                        .onFailure { storageError = "Could not save profile changes; the saved file was preserved."; deleteTarget = null }
                }
            }) { Text("Delete", color = palette.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    shareTarget?.let { profile ->
        ShareDialog(
            profile = profile,
            shareLink = shareLink,
            shareCopied = shareCopied,
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Netray config", shareLink))
                shareCopied = true
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            onClose = { shareTarget = null; shareCopied = false }
        )
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    selected: Boolean,
    connected: Boolean,
    storeReady: Boolean,
    pingResult: String?,
    pinging: Boolean,
    onPing: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit
) {
    val palette = LocalNetrayPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(palette.card, RoundedCornerShape(24.dp))
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp)) else Modifier)
            .clickable(enabled = !connected, onClick = onSelect)
            .padding(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_server),
                contentDescription = null,
                tint = palette.serverIcon,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = profile.name,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                profile.cardTags.forEachIndexed { index, badge -> Tag(badge, active = index == 0) }
            }
        }
        Text(
            text = profile.endpoint,
            color = palette.textSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp)
        )
        HorizontalDivider(Modifier.padding(top = 10.dp), color = palette.divider)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pingResult != null) {
                Text(
                    text = pingResult,
                    color = if (pingResult == "Failed") palette.error else palette.latency,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(palette.latencySurface, RoundedCornerShape(18.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(38.dp)
                    .background(palette.primary, CircleShape)
                    .clickable(enabled = !pinging, onClick = onPing),
                contentAlignment = Alignment.Center
            ) {
                if (pinging) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = palette.onPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_activity),
                        contentDescription = "Test latency",
                        tint = palette.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onShare, modifier = Modifier.size(38.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_share_2),
                    contentDescription = "Share profile",
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                enabled = !connected && storeReady,
                onClick = onEdit,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_pencil),
                    contentDescription = "Edit profile",
                    tint = if (connected || !storeReady) palette.textSecondary.copy(alpha = .45f) else palette.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                enabled = !connected && storeReady,
                onClick = onDelete,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_trash_2),
                    contentDescription = "Delete profile",
                    tint = if (connected || !storeReady) palette.textSecondary.copy(alpha = .45f) else palette.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusLabel(
    state: NetrayVpnService.State,
    proxyState: NetrayProxyService.State,
    profileId: String,
    proxyHttpPort: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vpnConnected = state is NetrayVpnService.State.Connected
    val proxyConnected = proxyState is NetrayProxyService.State.Connected
    val connected = vpnConnected || proxyConnected
    var uptime by remember { mutableLongStateOf(0) }
    var connectedPingMs by remember { mutableStateOf<Long?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(state, proxyState, lifecycle) {
        if (connected) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val startedAt = if (vpnConnected) NetrayVpnService.connectedAt else NetrayProxyService.connectedAt
                    uptime = (android.os.SystemClock.elapsedRealtime() - startedAt) / 1000
                    delay(1000)
                }
            }
        } else {
            uptime = 0
        }
    }

    LaunchedEffect(state, proxyState, profileId, proxyHttpPort) {
        if (connected) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    if (profileId.isNotBlank()) {
                        val result = try {
                            withContext(Dispatchers.IO) {
                                if (proxyConnected) httpPingViaProxy(proxyHttpPort) else httpPing()
                            }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            "Failed"
                        }
                        connectedPingMs = if (result.endsWith(" ms")) result.removeSuffix(" ms").toLongOrNull() else null
                    }
                    delay(5000)
                }
            }
        } else {
            connectedPingMs = null
        }
    }

    val pingPart = connectedPingMs?.let { " · ${it} ms" }.orEmpty()
    val status = when {
        proxyConnected -> "PROXY · ${formatUptime(uptime)}$pingPart"
        vpnConnected -> "CONNECTED · ${formatUptime(uptime)}$pingPart"
        proxyState is NetrayProxyService.State.Connecting || state is NetrayVpnService.State.Connecting -> "CONNECTING…"
        proxyState is NetrayProxyService.State.Stopping || state is NetrayVpnService.State.Stopping -> "STOPPING…"
        proxyState is NetrayProxyService.State.Error || state is NetrayVpnService.State.Error -> "CONNECTION ERROR"
        else -> "DISCONNECTED"
    }
    Text(
        text = "●  $status",
        modifier = modifier.clickable(onClick = onClick),
        color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun Tag(text: String, active: Boolean = false) {
    val palette = LocalNetrayPalette.current
    Text(
        text = text,
        color = if (active) palette.latency else palette.textSecondary,
        fontSize = 11.sp,
        modifier = Modifier
            .background(if (active) palette.activeTag else palette.inactiveTag, RoundedCornerShape(6.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp)
    )
}

@Composable
private fun ProtocolBadge(kind: String) {
    val palette = LocalNetrayPalette.current
    val background = if (kind == "NEW") palette.badgeNew else if (kind == "NATIVE") palette.badgeNative else palette.badgeXray
    Text(
        text = kind,
        color = palette.onBadge,
        fontSize = 12.sp,
        modifier = Modifier
            .background(background, RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

private suspend fun readImportFile(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val output = ByteArrayOutputStream()
    val input = context.contentResolver.openInputStream(uri) ?: error("Could not open selected file")
    input.use { stream ->
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            total += count
            require(total <= 4 * 1024 * 1024) { "Import file is larger than 4 MB" }
            output.write(buffer, 0, count)
        }
    }
    output.toString(Charsets.UTF_8.name()).also { require(it.isNotBlank()) { "Selected file is empty" } }
}

private suspend fun readQrFromImage(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    QrImageDecoder.decode(context, uri).takeIf(List<String>::isNotEmpty)
        ?.joinToString("\n") ?: error(context.getString(R.string.error_qr_image))
}

private fun startTunnel(context: android.content.Context, profileId: String?, mode: Int = 0, disconnect: Boolean = false) {
    val intent = Intent(context, NetrayVpnService::class.java).setAction(if (disconnect) NetrayVpnService.ACTION_DISCONNECT else NetrayVpnService.ACTION_CONNECT)
        .putExtra(NetrayVpnService.EXTRA_PROFILE, profileId)
        .putExtra(NetrayVpnService.EXTRA_MODE, mode)
    if (disconnect) context.startService(intent) else ContextCompat.startForegroundService(context, intent)
}

private fun startProxyTunnel(context: android.content.Context, profileId: String?, mode: Int = 1, disconnect: Boolean = false) {
    val prefs = context.getSharedPreferences("settings", 0)
    val ports = ProxyConfig.loadPorts(prefs)
    val intent = Intent(context, NetrayProxyService::class.java).setAction(if (disconnect) NetrayProxyService.ACTION_DISCONNECT else NetrayProxyService.ACTION_CONNECT)
        .putExtra(NetrayProxyService.EXTRA_PROFILE, profileId)
        .putExtra(NetrayProxyService.EXTRA_SOCKS_PORT, ports[0])
        .putExtra(NetrayProxyService.EXTRA_HTTP_PORT, ports[1])
    if (disconnect) context.startService(intent) else ContextCompat.startForegroundService(context, intent)
}

private fun formatUptime(seconds: Long): String = "%02d:%02d:%02d".format(
    TimeUnit.SECONDS.toHours(seconds), TimeUnit.SECONDS.toMinutes(seconds) % 60, seconds % 60
)

/// Mirrors the Linux reference's latency::response_latency: performs an HTTPS GET
/// to 1.1.1.1/cdn-cgi/trace through the tunnel/proxy and measures round-trip time.
private fun httpPing(timeoutMs: Int = 5000): String {
    return runCatching {
        val start = android.os.SystemClock.elapsedRealtime()
        val url = URL("https://1.1.1.1/cdn-cgi/trace")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            useCaches = false
            setRequestProperty("Host", "1.1.1.1")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Connection", "close")
        }
        try {
            val code = conn.responseCode
            if (code in 200..399) {
                "${android.os.SystemClock.elapsedRealtime() - start} ms"
            } else {
                "Failed (HTTP $code)"
            }
        } finally {
            conn.disconnect()
        }
    }.getOrElse { "Failed" }
}

/// Pings through the local HTTP proxy inbound (127.0.0.1:proxyPort) by targeting
/// an external host. The proxy forwards the request, so this measures the real
/// proxied path, not the direct internet.
private fun httpPingViaProxy(proxyPort: Int, timeoutMs: Int = 5000): String {
    return runCatching {
        val start = android.os.SystemClock.elapsedRealtime()
        val proxy = java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", proxyPort))
        val connection = URL("https://1.1.1.1/cdn-cgi/trace").openConnection(proxy) as HttpURLConnection
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.useCaches = false
        connection.setRequestProperty("Host", "1.1.1.1")
        connection.setRequestProperty("Cache-Control", "no-cache")
        connection.setRequestProperty("Connection", "close")
        try {
            val code = connection.responseCode
            if (code in 200..399) {
                "${android.os.SystemClock.elapsedRealtime() - start} ms"
            } else {
                "Failed (HTTP $code)"
            }
        } finally {
            connection.disconnect()
        }
    }.getOrElse { "Failed" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareDialog(
    profile: Profile,
    shareLink: String,
    shareCopied: Boolean,
    onCopy: () -> Unit,
    onClose: () -> Unit
) {
    val palette = LocalNetrayPalette.current
    val qrBitmap by produceState<Bitmap?>(initialValue = null, shareLink) {
        value = withContext(Dispatchers.Default) {
            runCatching { createQrBitmap(shareLink) }.getOrNull()
        }
    }
    val dialogSurface = palette.surface
    val dialogCard = palette.surfaceVariant
    val dialogText = palette.textPrimary
    val dialogTextVariant = palette.textSecondary

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("QR Code & Copy", color = dialogText) },
        text = { Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(palette.qrBackground, RoundedCornerShape(8.dp))
                    .padding(12.dp)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap!!.asImageBitmap(),
                        contentDescription = "QR code for ${profile.name}",
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f)
                    )
                } else {
                    Text(
                        text = "Could not generate QR code for this link.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = dialogTextVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // Hint
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_info),
                    contentDescription = null,
                    tint = dialogTextVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Consider using file export if configuration too complex",
                    style = MaterialTheme.typography.bodySmall,
                    color = dialogTextVariant
                )
            }
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = dialogCard
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_link),
                        contentDescription = null,
                        tint = dialogTextVariant,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Link",
                            style = MaterialTheme.typography.titleMedium,
                            color = dialogTextVariant
                        )
                        Text(
                            text = shareLink,
                            style = MaterialTheme.typography.bodyLarge,
                            color = dialogText,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lucide_copy),
                            contentDescription = "Copy link",
                            tint = if (shareCopied) palette.success else dialogTextVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            if (shareCopied) {
                Text(
                    text = "Copied to clipboard",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.success,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp)
                )
            }
        } },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        containerColor = dialogSurface
    )
}

private fun createQrBitmap(content: String): Bitmap {
    require(content.isNotBlank()) { "QR content cannot be empty" }
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 2
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 512, 512, hints)
    val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}
