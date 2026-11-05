package com.venujatech.netray

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

@Composable
private fun NetrayScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ProfileStore(context) }
    val profiles = remember { mutableStateListOf<Profile>() }
    val pingResults = remember { mutableStateMapOf<String, String>() }
    val pinging = remember { mutableStateMapOf<String, Boolean>() }
    var selectedId by rememberSaveable { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf("") }
    var storageError by remember { mutableStateOf("") }
    var storeReady by remember { mutableStateOf(false) }
    var notificationError by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Profile?>(null) }
    var editTarget by remember { mutableStateOf<Profile?>(null) }
    var editLink by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val settings = remember { context.getSharedPreferences("settings", 0) }
    var allowInsecureTls by remember { mutableStateOf(settings.getBoolean("allow_insecure_tls", false)) }
    var showAbout by remember { mutableStateOf(false) }
    var showConnectionError by remember { mutableStateOf(false) }
    var uptime by remember { mutableLongStateOf(0) }
    val state by NetrayVpnService.state.collectAsStateWithLifecycle()
    val vpn = state is NetrayVpnService.State.Connected
    val busy = state is NetrayVpnService.State.Connecting || state is NetrayVpnService.State.Stopping
    val selectedProfile = profiles.firstOrNull { it.id == selectedId }

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

    var pendingProfileId by remember { mutableStateOf("") }
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

    MaterialTheme(colorScheme = darkColorScheme(background = Background, surface = Card, primary = Blue, onBackground = Color.White, onSurface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = Background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp)) {
                Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Text("☰", color = Color.White, fontSize = 24.sp,
                            modifier = Modifier.semantics { contentDescription = "Open menu" }.clickable { menuExpanded = true }.padding(end = 16.dp))
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Import VLESS link") }, onClick = { menuExpanded = false; showImport = true })
                            DropdownMenuItem(text = { Text("Settings") }, onClick = { menuExpanded = false; showSettings = true })
                            DropdownMenuItem(text = { Text("About Netray") }, onClick = { menuExpanded = false; showAbout = true })
                        }
                    }
                    Box(Modifier.size(34.dp).background(Color(0xFF171C25), CircleShape), contentAlignment = Alignment.Center) {
                        Text("N", color = Blue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Netray", Modifier.padding(start = 12.dp).weight(1f), fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                    Text("＋", color = Color.White, fontSize = 25.sp,
                        modifier = Modifier.semantics { contentDescription = "Add VLESS profile" }.clickable(enabled = storeReady) { showImport = true }.padding(horizontal = 10.dp, vertical = 8.dp))
                    Box {
                        Text("⋮", color = Color.White, fontSize = 24.sp,
                            modifier = Modifier.semantics { contentDescription = "More options" }.clickable { menuExpanded = true }.padding(horizontal = 6.dp, vertical = 8.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    ModeChip("☑  TUN", selected = true)
                    Spacer(Modifier.width(6.dp))
                    ModeChip("□  Proxy", selected = false)
                    Spacer(Modifier.width(6.dp))
                    Spacer(Modifier.weight(1f))
                    Text(if (vpn) "Tunnel active" else if (busy) "Connecting" else "Offline", color = if (vpn) Green else Muted, fontSize = 12.sp)
                }
                if (storageError.isNotEmpty()) Text(storageError, color = Color(0xFFFF807D), fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
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
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val status = when (state) {
                        NetrayVpnService.State.Connected -> "CONNECTED · ${formatUptime(uptime)}"
                        NetrayVpnService.State.Connecting -> "CONNECTING"
                        is NetrayVpnService.State.Error -> "CONNECTION ERROR"
                        else -> "DISCONNECTED"
                    }
                    Text("●  $status", Modifier.weight(1f).clickable { showConnectionError = true }, color = if (vpn) Green else Muted, fontSize = 13.sp)
                    Box(Modifier.size(64.dp).background(Blue, CircleShape).clickable(enabled = !busy && (vpn || selectedProfile != null)) {
                        if (vpn) startTunnel(context, null, disconnect = true)
                        else if (android.os.Build.VERSION.SDK_INT >= 33 &&
                            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        else requestVpnConsent()
                    }, contentAlignment = Alignment.Center) {
                        Text(if (vpn) "■" else if (busy) "…" else "▶", fontSize = 24.sp, color = Color.White)
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
                            .onSuccess { profiles.clear(); profiles.addAll(next); editTarget = null; editError = "" }
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

    if (showSettings) AlertDialog(
        onDismissRequest = { showSettings = false },
        title = { Text("Settings", color = Color.White) },
        text = { Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Allow insecure (TLS)", color = Color.White, modifier = Modifier.weight(1f))
                Switch(checked = allowInsecureTls, enabled = !vpn && !busy, onCheckedChange = {
                    allowInsecureTls = it
                    settings.edit().putBoolean("allow_insecure_tls", it).apply()
                })
            }
            Text("Skips server certificate verification. Turn off for verified TLS. Disconnect before changing this setting.", color = Muted)
        } },
        confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Close", color = Blue) } },
        containerColor = Card
    )

    if (showAbout) AlertDialog(
        onDismissRequest = { showAbout = false },
        title = { Text("Netray for Android") },
        text = { Text("Version 0.1.0 · Xray-core via XTLS/libXray") },
        confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Close") } },
        containerColor = Card
    )

    if (showConnectionError) {
        var logLines by remember { mutableStateOf(listOf("Loading connection logs…")) }
        LaunchedEffect(Unit) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    logLines = withContext(Dispatchers.IO) { ConnectionLogs.read() }
                    delay(2000)
                }
            }
        }
        AlertDialog(
            onDismissRequest = { showConnectionError = false },
            title = { Text("Connection logs", color = Color.White) },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                    items(logLines) { line ->
                        Text(line, color = Color(0xFFDEE5EF), fontSize = 14.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 3.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showConnectionError = false }) { Text("Close", color = Blue) } },
            containerColor = Card
        )
    }

}

@Composable
private fun ModeChip(label: String, selected: Boolean) {
    Text(label, color = if (selected) Color(0xFFBDEBFF) else Muted, fontSize = 13.sp,
        modifier = Modifier.background(if (selected) Color(0xFF15304D) else Card, RoundedCornerShape(9.dp)).padding(horizontal = 12.dp, vertical = 9.dp))
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
    Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(24.dp))
        .then(if (selected) Modifier.border(2.dp, Color(0xFF05B9F3), RoundedCornerShape(24.dp)) else Modifier)
        .clickable(enabled = !connected, onClick = onSelect).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("♙", color = Color(0xFF18C9FF), fontSize = 16.sp)
            Text(profile.name, Modifier.weight(1f).padding(start = 8.dp), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
            if (selected) Text("● SELECTED", color = Green, fontSize = 10.sp, modifier = Modifier.background(Color(0xFF08372F), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
        }
        Text("${profile.server}:${profile.port}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag("VLESS", true); Tag(profile.transport.uppercase()); if (profile.security != "none") Tag(profile.security.uppercase())
        }
        HorizontalDivider(Modifier.padding(top = 14.dp), color = Color(0xFF2B3340))
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(pingResult ?: profile.protocolLabel(),
                color = if (pingResult == "Failed") Color(0xFFFF807D) else Color(0xFF60CFFF), fontSize = 12.sp,
                modifier = if (pingResult != null) Modifier.background(Color(0xFF111821), RoundedCornerShape(18.dp)).padding(horizontal = 10.dp, vertical = 7.dp) else Modifier)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(40.dp).background(Blue, CircleShape).clickable(enabled = !pinging, onClick = onPing), contentAlignment = Alignment.Center) {
                Text(if (pinging) "…" else "∿", color = Color.White, fontSize = 21.sp)
            }
            Text("↗", color = Muted, fontSize = 20.sp, modifier = Modifier.clickable(onClick = onShare).padding(start = 15.dp).size(28.dp).wrapContentSize(Alignment.Center))
            Text("✎", color = if (connected || !storeReady) Muted.copy(alpha = .45f) else Muted, fontSize = 20.sp,
                modifier = Modifier.clickable(enabled = !connected && storeReady, onClick = onEdit).padding(start = 12.dp).size(28.dp).wrapContentSize(Alignment.Center))
            Text("▤", color = if (connected || !storeReady) Muted.copy(alpha = .45f) else Muted, fontSize = 18.sp,
                modifier = Modifier.clickable(enabled = !connected && storeReady, onClick = onDelete).padding(start = 12.dp).size(28.dp).wrapContentSize(Alignment.Center))
        }
    }
}

@Composable
private fun Tag(text: String, active: Boolean = false) {
    Text(text, color = if (active) Color(0xFF60CFFF) else Muted, fontSize = 11.sp,
        modifier = Modifier.background(if (active) Color(0xFF27364A) else Color(0xFF222833), RoundedCornerShape(6.dp)).padding(horizontal = 9.dp, vertical = 6.dp))
}

private fun Profile.protocolLabel() = "VLESS  ·  ${transport.uppercase()}  ·  ${security.uppercase()}"

private fun startTunnel(context: android.content.Context, profileId: String?, disconnect: Boolean = false) {
    val intent = Intent(context, NetrayVpnService::class.java).setAction(if (disconnect) NetrayVpnService.ACTION_DISCONNECT else NetrayVpnService.ACTION_CONNECT)
        .putExtra(NetrayVpnService.EXTRA_PROFILE, profileId)
    if (disconnect) context.startService(intent) else ContextCompat.startForegroundService(context, intent)
}

private fun formatUptime(seconds: Long): String = "%02d:%02d:%02d".format(
    TimeUnit.SECONDS.toHours(seconds), TimeUnit.SECONDS.toMinutes(seconds) % 60, seconds % 60
)
