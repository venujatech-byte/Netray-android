package com.venujatech.netray

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
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
import com.venujatech.netray.ui.AboutScreen
import com.venujatech.netray.ui.CheckIpScreen
import com.venujatech.netray.ui.LogsScreen
import com.venujatech.netray.ui.SettingsScreen
import com.venujatech.netray.ui.theme.NetrayTheme
import com.venujatech.netray.vpn.NetrayVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var showImport by rememberSaveable { mutableStateOf(false) }
    var link by rememberSaveable { mutableStateOf("") }
    var importError by rememberSaveable { mutableStateOf("") }
    var storageError by remember { mutableStateOf("") }
    var storeReady by remember { mutableStateOf(false) }
    var notificationError by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Profile?>(null) }
    var editTarget by remember { mutableStateOf<Profile?>(null) }
    var editLink by rememberSaveable { mutableStateOf("") }
    var editError by rememberSaveable { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    val settings = remember { context.getSharedPreferences("settings", 0) }
    var themeMode by rememberSaveable { mutableStateOf(settings.getString("theme_mode", "system") ?: "system") }
    var uptime by remember { mutableLongStateOf(0) }
    val state by NetrayVpnService.state.collectAsStateWithLifecycle()
    val vpn = state is NetrayVpnService.State.Connected
    val busy = state is NetrayVpnService.State.Connecting || state is NetrayVpnService.State.Stopping
    val selectedProfile = profiles.firstOrNull { it.id == selectedId }

    var currentScreen by rememberSaveable { mutableStateOf("configs") }
    var selectedMode by rememberSaveable { mutableIntStateOf(0) }
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
    LaunchedEffect(state, lifecycle) {
        if (vpn) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                uptime = (android.os.SystemClock.elapsedRealtime() - NetrayVpnService.connectedAt) / 1000
                delay(1000)
            }
        }
    }

    var pendingProfileId by rememberSaveable { mutableStateOf("") }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingProfileId.isNotBlank()) startTunnel(context, pendingProfileId)
        pendingProfileId = ""
    }
    val requestVpnConsent: () -> Unit = {
        val prepare = VpnService.prepare(context)
        if (prepare == null) startTunnel(context, selectedId)
        else { pendingProfileId = selectedId; consent.launch(prepare) }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) requestVpnConsent()
        else notificationError = "Allow notifications so Android can show the VPN's background status."
    }

    NetrayTheme(themeMode = themeMode) {
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
                    vpnBusy = vpn || busy,
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
                                        if (swipeDrag > 60f || swipeDrag < -60f || velocity > 300f || velocity < -300f) {
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
                                        painter = painterResource(R.drawable.ic_lucide_plus),
                                        contentDescription = "Add VLESS profile",
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

                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp)
                            ) {
                                SegmentedButton(
                                    selected = selectedMode == 0,
                                    onClick = { selectedMode = 0 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                    label = { Text("TUN") }
                                )
                                SegmentedButton(
                                    selected = selectedMode == 1,
                                    onClick = { selectedMode = 1 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                    label = { Text("Proxy") }
                                )
                            }

                            if (storageError.isNotEmpty()) Text(storageError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                                items(profiles, key = { it.id }) { profile ->
                                    ProfileCard(profile, selected = profile.id == selectedId, connected = vpn || busy, storeReady = storeReady,
                                        pingResult = pingResults[profile.id], pinging = pinging[profile.id] == true,
                                        onPing = {
                                            if (pinging[profile.id] != true) scope.launch {
                                                pinging[profile.id] = true
                                                pingResults.remove(profile.id)
                                                val result = withContext(Dispatchers.IO) {
                                                    runCatching {
                                                        val start = android.os.SystemClock.elapsedRealtime()
                                                        java.net.Socket().use { it.connect(java.net.InetSocketAddress(profile.server, profile.port), 3000) }
                                                        "TCP ${android.os.SystemClock.elapsedRealtime() - start} ms"
                                                    }.getOrElse { "Failed" }
                                                }
                                                pingResults[profile.id] = result
                                                pinging[profile.id] = false
                                            }
                                        },
                                        onSelect = { selectedId = profile.id }, onDelete = { deleteTarget = profile },
                                        onEdit = { editLink = profile.link; editTarget = profile },
                                        onShare = {
                                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, profile.link)
                                            context.startActivity(Intent.createChooser(send, "Share VLESS profile"))
                                        })
                                }
                            }

                            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                val status = when (state) {
                                    NetrayVpnService.State.Connected -> "CONNECTED · ${formatUptime(uptime)}"
                                    NetrayVpnService.State.Connecting -> "CONNECTING…"
                                    NetrayVpnService.State.Stopping -> "STOPPING…"
                                    is NetrayVpnService.State.Error -> "CONNECTION ERROR"
                                    else -> "DISCONNECTED"
                                }
                                Text(
                                    text = "●  $status",
                                    modifier = Modifier.weight(1f).clickable { currentScreen = "logs" },
                                    color = if (vpn) Green else Muted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                FilledIconButton(
                                    onClick = {
                                        if (vpn) startTunnel(context, null, disconnect = true)
                                        else if (android.os.Build.VERSION.SDK_INT >= 33 &&
                                            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                                            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        else requestVpnConsent()
                                    },
                                    enabled = !busy && (vpn || selectedProfile != null),
                                    modifier = Modifier.size(64.dp),
                                    shape = CircleShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = if (vpn) MaterialTheme.colorScheme.primaryContainer else Blue,
                                        contentColor = if (vpn) MaterialTheme.colorScheme.primary else Color.White
                                    )
                                ) {
                                    if (busy) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.White, strokeWidth = 2.5.dp)
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_lucide_power),
                                            contentDescription = if (vpn) "Disconnect VPN" else "Connect VPN",
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

    if (showImport) AlertDialog(
        onDismissRequest = { showImport = false; importError = "" },
        title = { Text("Add VLESS profile") },
        text = { Column {
            OutlinedTextField(link, { link = it; importError = "" }, label = { Text("VLESS link") }, minLines = 3)
            if (importError.isNotEmpty()) Text(importError, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(onClick = {
            runCatching { Profile.parse(link) }.onSuccess { profile ->
                scope.launch {
                        if (!storeReady) { importError = "Saved profiles are unavailable; restart before changing them."; return@launch }
                        runCatching { withContext(Dispatchers.IO) { store.save(profiles + profile) } }
                        .onSuccess { profiles.add(profile); selectedId = profile.id; link = ""; showImport = false }
                        .onFailure { importError = "Could not securely save profile" }
                }
            }.onFailure { importError = it.message ?: "Invalid VLESS link" }
        }) { Text("Add") } },
        dismissButton = { TextButton(onClick = { showImport = false }) { Text("Cancel") } },
        containerColor = Card
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
        containerColor = Card
    ) }

    if (notificationError.isNotEmpty()) AlertDialog(
        onDismissRequest = { notificationError = "" },
        title = { Text("Notifications are disabled") },
        text = { Text(notificationError) },
        confirmButton = { TextButton(onClick = { notificationError = "" }) { Text("OK") } },
        containerColor = Card
    )

    deleteTarget?.let { profile -> AlertDialog(
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
        containerColor = Card
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

private fun startTunnel(context: android.content.Context, profileId: String?, disconnect: Boolean = false) {
    val intent = Intent(context, NetrayVpnService::class.java).setAction(if (disconnect) NetrayVpnService.ACTION_DISCONNECT else NetrayVpnService.ACTION_CONNECT)
        .putExtra(NetrayVpnService.EXTRA_PROFILE, profileId)
    if (disconnect) context.startService(intent) else ContextCompat.startForegroundService(context, intent)
}

private fun formatUptime(seconds: Long): String = "%02d:%02d:%02d".format(
    TimeUnit.SECONDS.toHours(seconds), TimeUnit.SECONDS.toMinutes(seconds) % 60, seconds % 60
)
