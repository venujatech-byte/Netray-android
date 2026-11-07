package com.venujatech.netray

import android.app.Activity
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.venujatech.netray.profiles.Profile
import com.venujatech.netray.profiles.ProfileStore
import com.venujatech.netray.profiles.NativeBackend
import com.venujatech.netray.proxy.NetrayProxyService
import com.venujatech.netray.proxy.ProxyConfig
import com.venujatech.netray.ui.AboutScreen
import com.venujatech.netray.ui.CheckIpScreen
import com.venujatech.netray.ui.LogsScreen
import com.venujatech.netray.ui.QrScannerScreen
import com.venujatech.netray.ui.SettingsScreen
import com.venujatech.netray.ui.theme.NetrayTheme
import com.venujatech.netray.vpn.NetrayVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

private val Background = Color(0xFF0B0E13)
private val Card = Color(0xFF1B202A)
private val Blue = Color(0xFF2788FF)
private val Muted = Color(0xFF93A1B5)
private val Green = Color(0xFF36D399)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    var editLink by rememberSaveable { mutableStateOf("") }
    var editError by rememberSaveable { mutableStateOf("") }
    var shareTarget by remember { mutableStateOf<Profile?>(null) }
    var shareLink by remember { mutableStateOf("") }
    var shareCopied by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var showImportConfirm by rememberSaveable { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var pendingImportLink by rememberSaveable { mutableStateOf("") }
    var pendingImportProfile by remember { mutableStateOf<Profile?>(null) }
    var importConfirmError by rememberSaveable { mutableStateOf("") }
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
    var uptime by remember { mutableLongStateOf(0) }
    var connectedPingMs by remember { mutableStateOf<Long?>(null) }
    var connectedPinging by remember { mutableStateOf(false) }
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

    BackHandler(enabled = drawerState.isOpen || currentScreen != "configs") {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
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
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state, proxyState, lifecycle) {
        if (connected) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val startedAt = if (vpn) NetrayVpnService.connectedAt else NetrayProxyService.connectedAt
                uptime = (android.os.SystemClock.elapsedRealtime() - startedAt) / 1000
                delay(1000)
            }
        } else {
            uptime = 0
        }
    }

    LaunchedEffect(state, proxyState) {
        val isConnected = vpn || proxyConnected
        if (isConnected) {
            while (true) {
                if (selectedId.isNotBlank() && !connectedPinging) {
                    val profile = profiles.firstOrNull { it.id == selectedId }
                    if (profile != null) {
                        connectedPinging = true
                        val result = withContext(Dispatchers.IO) {
                            if (proxyConnected) {
                                // Ping through the local HTTP proxy inbound
                                httpPingViaProxy(proxyHttpPort)
                            } else {
                                httpPing()
                            }
                        }
                        connectedPingMs = if (result.endsWith(" ms")) result.removeSuffix(" ms").toLongOrNull() else null
                        connectedPinging = false
                    }
                }
                delay(5000)
            }
        } else {
            connectedPingMs = null
            connectedPinging = false
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

    NetrayTheme(themeMode = themeMode) {
        if (showQrScanner) {
            QrScannerScreen(
                onScanResult = { raw ->
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { Profile.parse(raw) } }
                            .onSuccess { profile ->
                                pendingImportProfile = profile
                                showQrScanner = false
                                showImportConfirm = true
                            }
                            .onFailure { importError = it.message ?: "Invalid QR config"; showQrScanner = false }
                    }
                },
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
                            Text("v0.1.0", style = MaterialTheme.typography.bodySmall, color = Muted)
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
                    vpnBusy = anyBusy,
                    onCheckForUpdates = { currentScreen = "check_ip" },
                    onViewLogs = { currentScreen = "logs" }
                )
                "about" -> AboutScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } }
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
                                IconButton(
                                    onClick = { showImport = true },
                                    enabled = storeReady,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_add_profile),
                                        contentDescription = "Add VLESS profile",
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { showQrScanner = true },
                                    enabled = storeReady,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_qr_scan),
                                        contentDescription = "Scan QR code to import",
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = clipboard.primaryClip
                                        val text = clip?.getItemAt(0)?.text?.toString().orEmpty().trim()
                                        if (text.isBlank()) {
                                            importError = "Clipboard is empty"
                                            showImport = true
                                        } else {
                                            pendingImportLink = text
                                            showImportConfirm = true
                                        }
                                    },
                                    enabled = storeReady,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_clipboard_import),
                                        contentDescription = "Import config from clipboard",
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(24.dp)
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
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Import VLESS link") },
                                            onClick = { menuExpanded = false; showImport = true }
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
                                        onEdit = { editLink = profile.link; editTarget = profile },
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
                                val status = when {
                                    proxyConnected -> {
                                        val pingPart = connectedPingMs?.let { " · ${it} ms" }.orEmpty()
                                        "PROXY · ${formatUptime(uptime)}$pingPart"
                                    }
                                    vpn -> {
                                        val pingPart = connectedPingMs?.let { " · ${it} ms" }.orEmpty()
                                        "CONNECTED · ${formatUptime(uptime)}$pingPart"
                                    }
                                    proxyState is NetrayProxyService.State.Connecting || state is NetrayVpnService.State.Connecting -> "CONNECTING…"
                                    proxyState is NetrayProxyService.State.Stopping || state is NetrayVpnService.State.Stopping -> "STOPPING…"
                                    proxyState is NetrayProxyService.State.Error || state is NetrayVpnService.State.Error -> "CONNECTION ERROR"
                                    else -> "DISCONNECTED"
                                }
                                Text(
                                    text = "●  $status",
                                    modifier = Modifier.weight(1f).clickable { currentScreen = "logs" },
                                    color = if (connected) Green else Muted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
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
                                        containerColor = if (connected) MaterialTheme.colorScheme.primaryContainer else Blue,
                                        contentColor = if (connected) MaterialTheme.colorScheme.primary else Color.White
                                    )
                                ) {
                                    if (anyBusy) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.White, strokeWidth = 2.5.dp)
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_lucide_power),
                                            contentDescription = if (connected) "Disconnect" else "Connect",
                                            modifier = Modifier.size(28.dp)
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

    if (showImport) AlertDialog(
        onDismissRequest = { showImport = false; importError = "" },
        title = { Text("Add VLESS profile") },
        text = { Column {
            OutlinedTextField(link, { link = it; importError = "" }, label = { Text("VLESS link") }, minLines = 3)
            if (importError.isNotEmpty()) Text(importError, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(onClick = {
            runCatching { Profile.parse(link) }.onSuccess { profile ->
                pendingImportProfile = profile
                link = ""
                showImport = false
                showImportConfirm = true
            }.onFailure { importError = it.message ?: "Invalid VLESS link" }
        }) { Text("Add") } },
        dismissButton = { TextButton(onClick = { showImport = false }) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface
    )

    if (showImportConfirm && pendingImportProfile != null) AlertDialog(
        onDismissRequest = { showImportConfirm = false; pendingImportProfile = null; importConfirmError = "" },
        title = { Text("Import config") },
        text = { Column {
            Text(pendingImportProfile!!.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("${pendingImportProfile!!.server}:${pendingImportProfile!!.port}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("This will add the config to your list.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (importConfirmError.isNotEmpty()) Text(importConfirmError, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(onClick = {
            val profile = pendingImportProfile
            if (profile == null) { showImportConfirm = false; return@TextButton }
            scope.launch {
                if (!storeReady) { importConfirmError = "Saved profiles are unavailable; restart before changing them."; return@launch }
                runCatching { withContext(Dispatchers.IO) { store.save(profiles + profile) } }
                    .onSuccess {
                        profiles.add(profile)
                        selectedId = profile.id
                        showImportConfirm = false
                        pendingImportProfile = null
                    }
                    .onFailure { importConfirmError = "Could not securely save profile" }
            }
        }) { Text("Import") } },
        dismissButton = { TextButton(onClick = { showImportConfirm = false; pendingImportProfile = null; importConfirmError = "" }) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface
    )

    editTarget?.let { original -> AlertDialog(
        onDismissRequest = { editTarget = null },
        title = { Text("Edit VLESS profile") },
        text = { Column {
            OutlinedTextField(editLink, { editLink = it; editError = "" }, label = { Text("VLESS link") }, minLines = 3)
            if (editError.isNotEmpty()) Text(editError, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(onClick = {
            runCatching { Profile.parse(editLink).copy(id = original.id) }
                .onSuccess { edited ->
                    scope.launch {
                        val next = profiles.map { if (it.id == original.id) edited else it }
                        runCatching { withContext(Dispatchers.IO) { store.save(next) } }
                            .onSuccess {
                                profiles.clear()
                                profiles.addAll(next)
                                selectedId = edited.id
                                pingResults.remove(original.id)
                                pinging.remove(original.id)
                                editTarget = null
                                editError = ""
                            }
                            .onFailure { storageError = "Could not save profile changes; the saved file was preserved." }
                    }
                }
                .onFailure { editError = it.message ?: "Invalid VLESS link" }
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { editTarget = null; editError = "" }) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface
    ) }

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
            }) { Text("Delete", color = Color(0xFFFF7777)) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    shareTarget?.let { profile ->
        AlertDialog(
        onDismissRequest = { shareTarget = null; shareCopied = false },
        title = { Text("QR Code & Copy") },
        text = { Column {
            Text(
                text = "QR Code",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Scan to import this config",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                text = "Link",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = shareLink,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        } },
        confirmButton = { TextButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Netray config", shareLink))
            shareCopied = true
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }) {
            Text(if (shareCopied) "Copied!" else "Copy", color = if (shareCopied) Green else MaterialTheme.colorScheme.primary)
        } },
        dismissButton = { TextButton(onClick = { shareTarget = null; shareCopied = false }) { Text("Close") } },
        containerColor = MaterialTheme.colorScheme.surface
    ) }
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
    Column(
        Modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(24.dp))
            .then(if (selected) Modifier.border(2.dp, Color(0xFF05B9F3), RoundedCornerShape(24.dp)) else Modifier)
            .clickable(enabled = !connected, onClick = onSelect)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_server),
                contentDescription = null,
                tint = Color(0xFF18C9FF),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = profile.name,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Color.White,
                maxLines = 1
            )
        }
        Text(
            text = "${profile.server}:${profile.port}",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 5.dp)
        )
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag("VLESS", true)
            Tag(profile.transport.uppercase())
            if (profile.security != "none") Tag(profile.security.uppercase())
        }
        HorizontalDivider(Modifier.padding(top = 14.dp), color = Color(0xFF2B3340))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pingResult != null) {
                Text(
                    text = pingResult,
                    color = if (pingResult == "Failed") Color(0xFFFF807D) else Color(0xFF60CFFF),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(Color(0xFF111821), RoundedCornerShape(18.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(38.dp)
                    .background(Blue, CircleShape)
                    .clickable(enabled = !pinging, onClick = onPing),
                contentAlignment = Alignment.Center
            ) {
                if (pinging) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_activity),
                        contentDescription = "Test latency",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onShare, modifier = Modifier.size(38.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_share_2),
                    contentDescription = "Share profile",
                    tint = Muted,
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
                    tint = if (connected || !storeReady) Muted.copy(alpha = .45f) else Muted,
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
                    tint = if (connected || !storeReady) Muted.copy(alpha = .45f) else Muted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun Tag(text: String, active: Boolean = false) {
    Text(
        text = text,
        color = if (active) Color(0xFF60CFFF) else Muted,
        fontSize = 11.sp,
        modifier = Modifier
            .background(if (active) Color(0xFF27364A) else Color(0xFF222833), RoundedCornerShape(6.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp)
    )
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
