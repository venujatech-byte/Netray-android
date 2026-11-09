package com.venujatech.netray.ui

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.venujatech.netray.R
import com.venujatech.netray.ui.theme.LocalNetrayPalette
import com.venujatech.netray.ui.theme.colorForAccent
import com.venujatech.netray.ui.theme.onColorFor
import com.venujatech.netray.vpn.DnsServerConfig

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
    dnsPrimary: String,
    dnsSecondary: String,
    onDnsServersChange: (String, String) -> Unit,
    bypassLan: Boolean,
    onBypassLanChange: (Boolean) -> Unit,
    ipv6Enabled: Boolean,
    onIpv6EnabledChange: (Boolean) -> Unit,
    accentColor: String,
    onAccentColorChange: (String) -> Unit,
    vpnBusy: Boolean,
    connected: Boolean,
    onOpenAppWhitelist: () -> Unit,
    onViewLogs: () -> Unit
) {
    var selectedMode by remember { mutableIntStateOf(settings.getInt("connection_mode", 0)) }
    var primaryDnsDraft by remember(dnsPrimary) { mutableStateOf(dnsPrimary) }
    var secondaryDnsDraft by remember(dnsSecondary) { mutableStateOf(dnsSecondary) }
    val primaryDnsValid = DnsServerConfig.normalize(primaryDnsDraft)
    val secondaryDnsValid = DnsServerConfig.normalize(secondaryDnsDraft)
    val duplicateDns = primaryDnsValid != null && primaryDnsValid == secondaryDnsValid

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
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            state = rememberLazyListState(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Appearance
            item(key = "appearance") {
                SettingsCard(title = "Appearance") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ThemeOption("System default", "system", themeMode, onThemeChange)
                        ThemeOption("Light theme", "light", themeMode, onThemeChange)
                        ThemeOption("Dark theme", "dark", themeMode, onThemeChange)
                    }
                }
            }

            // Accent Color
            item(key = "accent_color") {
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
                            AccentColorChip("Blue", colorForAccent("blue"), accentColor, onAccentColorChange)
                            AccentColorChip("Green", colorForAccent("green"), accentColor, onAccentColorChange)
                            AccentColorChip("Purple", colorForAccent("purple"), accentColor, onAccentColorChange)
                            AccentColorChip("Orange", colorForAccent("orange"), accentColor, onAccentColorChange)
                        }
                    }
                }
            }

            // Connection Mode
            item(key = "connection_mode") {
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
                                enabled = !vpnBusy && !connected,
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                label = { Text("TUN") }
                            )
                            SegmentedButton(
                                selected = selectedMode == 1,
                                onClick = { selectedMode = 1; onConnectionModeChange(1) },
                                enabled = !vpnBusy && !connected,
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                label = { Text("Proxy") }
                            )
                        }
                    }
                }
            }

            item(key = "dns_servers") {
                SettingsCard(title = "DNS Servers") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = primaryDnsDraft,
                            onValueChange = { primaryDnsDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Primary DNS") },
                            singleLine = true,
                            enabled = !connected && !vpnBusy,
                            isError = primaryDnsValid == null
                        )
                        OutlinedTextField(
                            value = secondaryDnsDraft,
                            onValueChange = { secondaryDnsDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Secondary DNS") },
                            singleLine = true,
                            enabled = !connected && !vpnBusy,
                            isError = secondaryDnsValid == null || duplicateDns
                        )
                        Text(
                            when {
                                connected -> "Disconnect before changing DNS. Saved values apply on the next connection."
                                duplicateDns -> "Primary and secondary DNS must be different."
                                primaryDnsValid == null || secondaryDnsValid == null -> "Enter valid IPv4 or IPv6 DNS addresses."
                                selectedMode == 1 -> "Xray uses these for its own DNS lookups in Proxy mode. Apps using the local proxy may still use device DNS."
                                else -> "TUN uses these DNS servers in order. Changes apply on the next connection."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (duplicateDns || primaryDnsValid == null || secondaryDnsValid == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { onDnsServersChange(primaryDnsValid!!, secondaryDnsValid!!) },
                            enabled = !connected && !vpnBusy && primaryDnsValid != null && secondaryDnsValid != null && !duplicateDns &&
                                (primaryDnsValid != dnsPrimary || secondaryDnsValid != dnsSecondary),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Apply DNS") }
                    }
                }
            }

            item(key = "bypass_lan") {
                SettingsCard(title = "Local Network") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwitchRow(
                            title = "Bypass LAN traffic",
                            subtitle = "Keep private, link-local, and local multicast traffic outside the VPN (for example, 192.168.1.1 and .local devices).",
                            checked = bypassLan,
                            enabled = !connected && !vpnBusy,
                            onCheckedChange = onBypassLanChange
                        )
                        Text(
                            "Disconnect before changing. The setting applies when you connect again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = "ipv6") {
                SettingsCard(title = "IPv6") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwitchRow(
                            title = "Enable IPv6 addresses",
                            subtitle = "Route IPv6 traffic through the VPN in TUN mode.",
                            checked = ipv6Enabled,
                            enabled = !connected && !vpnBusy,
                            onCheckedChange = onIpv6EnabledChange
                        )
                        Text(
                            when {
                                connected -> "Disconnect before changing. The setting applies when you connect again."
                                ipv6Enabled -> "When disabled, IPv6 traffic is blocked; IPv4 sites and apps can still connect."
                                else -> "IPv6 is disabled for TUN mode. IPv6-only destinations will not be reachable."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Proxy Ports
            item(key = "proxy_ports") {
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
            }

            // System & Startup
            item(key = "system_startup") {
                SettingsCard(title = "System & Startup") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        SwitchRow(
                            title = "Run at system startup",
                            subtitle = "After boot, connect if enabled below; otherwise show a notification to open Netray. Requires notification permission.",
                            checked = runAtStartup,
                            enabled = !vpnBusy,
                            onCheckedChange = onRunAtStartupChange
                        )
                        SwitchRow(
                            title = "Connect at startup",
                            subtitle = "Connect when Netray opens. Also connect after boot when Run at system startup is enabled.",
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
                            enabled = !vpnBusy && !connected,
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
            }

            item(key = "app_whitelist") {
                SettingsCard(title = "App Whitelist") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Choose apps and destinations that should bypass the Xray proxy routing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onOpenAppWhitelist, modifier = Modifier.fillMaxWidth()) {
                            Text("App Whitelist")
                        }
                    }
                }
            }

            // View Logs
            item(key = "view_logs") {
                OutlinedButton(
                    onClick = onViewLogs,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Connection Logs")
                }
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
    val palette = LocalNetrayPalette.current
    val contentColor = onColorFor(color)
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
                .border(if (isSelected) 3.dp else 0.dp, palette.textPrimary, CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_check),
                    contentDescription = null,
                    tint = contentColor,
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
