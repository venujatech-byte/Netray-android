package com.venujatech.netray.ui

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.venujatech.netray.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenDrawer: () -> Unit,
    settings: SharedPreferences,
    themeMode: String,
    onThemeChange: (String) -> Unit,
    connectionMode: Int,
    onConnectionModeChange: (Int) -> Unit,
    allowInsecureTls: Boolean,
    onAllowInsecureTlsChange: (Boolean) -> Unit,
    runAtStartup: Boolean,
    onRunAtStartupChange: (Boolean) -> Unit,
    connectAtStartup: Boolean,
    onConnectAtStartupChange: (Boolean) -> Unit,
    autoReconnect: Boolean,
    onAutoReconnectChange: (Boolean) -> Unit,
    notifyConnectionDrops: Boolean,
    onNotifyConnectionDropsChange: (Boolean) -> Unit,
    autoCheckUpdates: Boolean,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    instantImport: Boolean,
    onInstantImportChange: (Boolean) -> Unit,
    socksPort: Int,
    onSocksPortChange: (Int) -> Unit,
    httpPort: Int,
    onHttpPortChange: (Int) -> Unit,
    accentColor: String,
    onAccentColorChange: (String) -> Unit,
    vpnBusy: Boolean,
    onCheckForUpdates: () -> Unit,
    onViewLogs: () -> Unit
) {
    var selectedMode by remember { mutableIntStateOf(settings.getInt("connection_mode", 0)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(painterResource(R.drawable.ic_lucide_menu), contentDescription = "Open drawer")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Appearance
            SettingsCard(title = "Appearance") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ThemeOption("System default", "system", themeMode, onThemeChange)
                    ThemeOption("Light theme", "light", themeMode, onThemeChange)
                    ThemeOption("Dark theme", "dark", themeMode, onThemeChange)
                }
            }

            // Accent Color
            SettingsCard(title = "Accent Color") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Choose the accent color used for buttons, icons, and active states.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AccentColorChip("Blue", Color(0xFF2788FF), accentColor, onAccentColorChange)
                        AccentColorChip("Green", Color(0xFF36D399), accentColor, onAccentColorChange)
                        AccentColorChip("Purple", Color(0xFFB366FF), accentColor, onAccentColorChange)
                        AccentColorChip("Orange", Color(0xFFFF8C42), accentColor, onAccentColorChange)
                    }
                }
            }

            // Connection Mode
            SettingsCard(title = "Connection Mode") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "TUN routes all device traffic through the VPN. Proxy runs local SOCKS5/HTTP inbounds only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            selected = selectedMode == 0,
                            onClick = { selectedMode = 0; onConnectionModeChange(0) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            label = { Text("TUN") }
                        )
                        SegmentedButton(
                            selected = selectedMode == 1,
                            onClick = { selectedMode = 1; onConnectionModeChange(1) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            label = { Text("Proxy") }
                        )
                    }
                }
            }

            // Proxy Ports
            if (selectedMode == 1) {
                SettingsCard(title = "Proxy Ports") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("SOCKS5 port", style = MaterialTheme.typography.bodyLarge)
                                Text("Local SOCKS5 inbound port (127.0.0.1)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedTextField(
                                value = socksPort.toString(),
                                onValueChange = { newVal ->
                                    val parsed = newVal.toIntOrNull()
                                    if (parsed != null && parsed in 1024..65535) {
                                        onSocksPortChange(parsed)
                                    }
                                },
                                modifier = Modifier.width(120.dp)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("HTTP port", style = MaterialTheme.typography.bodyLarge)
                                Text("Local HTTP inbound port (127.0.0.1)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedTextField(
                                value = httpPort.toString(),
                                onValueChange = { newVal ->
                                    val parsed = newVal.toIntOrNull()
                                    if (parsed != null && parsed in 1024..65535) {
                                        onHttpPortChange(parsed)
                                    }
                                },
                                modifier = Modifier.width(120.dp)
                            )
                        }
                    }
                }
            }

            // System & Startup
            SettingsCard(title = "System & Startup") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SwitchRow(
                        title = "Run at system startup",
                        subtitle = "Start Netray automatically on device boot.",
                        checked = runAtStartup,
                        enabled = !vpnBusy,
                        onCheckedChange = onRunAtStartupChange
                    )
                    Spacer(Modifier.height(20.dp))

                    SwitchRow(
                        title = "Connect at startup",
                        subtitle = "Prompt to connect automatically on launch.",
                        checked = connectAtStartup,
                        enabled = !vpnBusy,
                        onCheckedChange = onConnectAtStartupChange
                    )
                    Spacer(Modifier.height(20.dp))

                    SwitchRow(
                        title = "Automatic restart after tunnel exit",
                        subtitle = "Restarts an exited tunnel. Network request failures keep it running.",
                        checked = autoReconnect,
                        enabled = !vpnBusy,
                        onCheckedChange = onAutoReconnectChange
                    )
                    Spacer(Modifier.height(20.dp))

                    SwitchRow(
                        title = "Connection drop notifications",
                        subtitle = "Shows a notification when the tunnel drops or reconnects.",
                        checked = notifyConnectionDrops,
                        enabled = !vpnBusy,
                        onCheckedChange = onNotifyConnectionDropsChange
                    )
                    Spacer(Modifier.height(20.dp))

                    SwitchRow(
                        title = "Allow insecure (TLS)",
                        subtitle = "Skips certificate validation. Turn off for verified TLS.",
                        checked = allowInsecureTls,
                        enabled = !vpnBusy,
                        onCheckedChange = onAllowInsecureTlsChange
                    )
                    Spacer(Modifier.height(20.dp))

                    SwitchRow(
                        title = "Instant import",
                        subtitle = "Import clipboard configs immediately without confirmation.",
                        checked = instantImport,
                        enabled = !vpnBusy,
                        onCheckedChange = onInstantImportChange
                    )
                    Spacer(Modifier.height(20.dp))

                    SwitchRow(
                        title = "Auto check for updates",
                        subtitle = "Checks for new releases at startup.",
                        checked = autoCheckUpdates,
                        enabled = !vpnBusy,
                        onCheckedChange = onAutoCheckUpdatesChange
                    )
                }
            }

            // App Updates
            SettingsCard(title = "App Updates") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Version 0.1.0",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(
                            onClick = onCheckForUpdates,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Check for Updates")
                        }
                    }
                }
            }

            // View Logs
            OutlinedButton(
                onClick = onViewLogs,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Connection Logs")
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ThemeOption(
    title: String,
    mode: String,
    selectedMode: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(mode) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = mode == selectedMode,
            onClick = { onSelect(mode) }
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AccentColorChip(
    name: String,
    color: Color,
    selected: String,
    onSelect: (String) -> Unit
) {
    val isSelected = selected == name.lowercase()
    Column(
        modifier = Modifier
            .clickable { onSelect(name.lowercase()) }
            .size(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(if (isSelected) 3.dp else 0.dp, Color.White, CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_check),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
