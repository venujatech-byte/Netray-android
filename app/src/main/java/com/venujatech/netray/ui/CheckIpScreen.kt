package com.venujatech.netray.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.venujatech.netray.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

data class IpDetails(
    val ip: String,
    val country: String,
    val isp: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckIpScreen(
    onOpenDrawer: () -> Unit,
    vpnConnected: Boolean
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<IpDetails?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val fetchIp: () -> Unit = {
        scope.launch {
            loading = true
            errorMessage = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val url = URL("https://ipwho.is/")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 8000
                        useCaches = false
                        setRequestProperty("User-Agent", "Netray-Android/0.1.0")
                    }
                    try {
                        val code = conn.responseCode
                        if (code in 200..299) {
                            val text = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                            val json = JSONObject(text)
                            if (json.optBoolean("success", true)) {
                                val ip = json.optString("ip", "Unknown")
                                val country = json.optString("country", "Unknown")
                                val isp = json.optJSONObject("connection")?.optString("isp")
                                    ?: json.optString("org", "Unknown")
                                IpDetails(ip, country, isp)
                            } else {
                                error(json.optString("message", "IP lookup failed"))
                            }
                        } else {
                            error("HTTP $code")
                        }
                    } finally {
                        conn.disconnect()
                    }
                }
            }.onSuccess {
                details = it
                loading = false
            }.onFailure {
                errorMessage = it.message ?: "Failed to check IP"
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchIp()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Check IP") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(painterResource(R.drawable.ic_lucide_menu), contentDescription = "Open drawer")
                    }
                },
                actions = {
                    IconButton(onClick = fetchIp, enabled = !loading) {
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(painterResource(R.drawable.ic_lucide_refresh_cw), contentDescription = "Refresh IP")
                        }
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
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lucide_shield),
                            contentDescription = null,
                            tint = if (vpnConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (vpnConnected) "Protected by Netray" else "Direct connection (VPN inactive)",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (vpnConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider()

                    IpField(label = "Public IP Address", value = details?.ip, loading = loading)
                    IpField(label = "Country", value = details?.country, loading = loading)
                    IpField(label = "ISP / Organization", value = details?.isp, loading = loading)
                }
            }

            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorMessage ?: "Error checking IP",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = fetchIp) {
                            Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IpField(label: String, value: String?, loading: Boolean) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = when {
                loading && value == null -> "Loading…"
                value != null -> value
                else -> "—"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

