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
    vpnBusy: Boolean,
    onViewLogs: () -> Unit
) {
    var allowInsecureTls by remember { mutableStateOf(settings.getBoolean("allow_insecure_tls", false)) }
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

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Connection", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Allow insecure (TLS)", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Skips certificate validation. Turn off for verified TLS. Disconnect before toggling.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = allowInsecureTls,
                            enabled = !vpnBusy,
                            onCheckedChange = {
                                allowInsecureTls = it
                                settings.edit().putBoolean("allow_insecure_tls", it).apply()
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        }
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

