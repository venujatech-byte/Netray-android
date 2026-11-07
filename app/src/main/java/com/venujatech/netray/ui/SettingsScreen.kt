package com.venujatech.netray.ui

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Appearance
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

                    ThemeOption("System default", "system", themeMode, onThemeChange)
                    ThemeOption("Light theme", "light", themeMode, onThemeChange)
                    ThemeOption("Dark theme", "dark", themeMode, onThemeChange)
                }
            }

            // Connection Mode
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Connection Mode", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    if (selectedMode == 1) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Proxy Ports", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    OutlinedButton(
                        onClick = onViewLogs,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View Connection Logs")
                    }
                }
            }

            // System & Startup
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("System & Startup", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Configure automatic startup and connection behavior.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SwitchRow(
                        title = "Run at system startup",
                        subtitle = "Start Netray automatically on device boot.",
                        checked = runAtStartup,
                        enabled = !vpnBusy,
                        onCheckedChange = onRunAtStartupChange
                    )

                    SwitchRow(
                        title = "Connect at startup",
                        subtitle = "Prompt to connect automatically on launch.",
                        checked = connectAtStartup,
                        enabled = !vpnBusy,
                        onCheckedChange = onConnectAtStartupChange
                    )

                    SwitchRow(
                        title = "Automatic restart after tunnel exit",
                        subtitle = "Restarts an exited tunnel. Network request failures keep it running.",
                        checked = autoReconnect,
                        enabled = !vpnBusy,
                        onCheckedChange = onAutoReconnectChange
                    )

                    SwitchRow(
                        title = "Connection drop notifications",
                        subtitle = "Shows a notification when the tunnel drops or reconnects.",
                        checked = notifyConnectionDrops,
                        enabled = !vpnBusy,
                        onCheckedChange = onNotifyConnectionDropsChange
                    )

                    SwitchRow(
                        title = "Allow insecure (TLS)",
                        subtitle = "Skips certificate validation. Turn off for verified TLS.",
                        checked = allowInsecureTls,
                        enabled = !vpnBusy,
                        onCheckedChange = onAllowInsecureTlsChange
                    )

                    SwitchRow(
                        title = "Instant import",
                        subtitle = "Import clipboard configs immediately without confirmation.",
                        checked = instantImport,
                        enabled = !vpnBusy,
                        onCheckedChange = onInstantImportChange
                    )

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
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("App Updates", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

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
