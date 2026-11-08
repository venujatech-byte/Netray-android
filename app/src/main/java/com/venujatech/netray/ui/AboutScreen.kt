package com.venujatech.netray.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.venujatech.netray.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onOpenDrawer: () -> Unit,
    appVersion: String,
    onCheckForUpdates: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About Netray") },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            Image(
                painter = painterResource(R.drawable.netray_logo),
                contentDescription = "Netray logo",
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
            )

            Text("Netray", style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.app_version, appVersion), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onCheckForUpdates, modifier = Modifier.fillMaxWidth()) {
                Text("Check for Updates")
            }

            Spacer(Modifier.height(8.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Core Engine", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Xray-core v26.10.31", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Packaged via XTLS/libXray with Rust-backed TUN interface integration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    Text("Open Source Licenses", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("• Xray-core: Mozilla Public License 2.0 (MPL-2.0)", style = MaterialTheme.typography.bodySmall)
                    Text("• libXray: MIT License", style = MaterialTheme.typography.bodySmall)
                    Text("• Lucide Icons: ISC License", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
