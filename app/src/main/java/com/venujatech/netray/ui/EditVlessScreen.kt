package com.venujatech.netray.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.net.toUri
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venujatech.netray.R
import com.venujatech.netray.profiles.Profile

private data class VlessEditDraft(
    val remarks: String,
    val address: String,
    val port: String,
    val encryption: String,
    val flow: String,
    val userId: String,
    val transport: String,
    val headerType: String,
    val path: String,
    val tlsType: String,
    val sni: String,
    val fingerprint: String,
    val alpn: String,
    val publicKey: String,
    val shortId: String
) {
    companion object {
        fun from(profile: Profile): VlessEditDraft {
            val uri = profile.link.toUri()
            fun query(name: String, fallback: String) =
                uri.getQueryParameter(name)?.takeIf(String::isNotBlank) ?: fallback

            return VlessEditDraft(
                remarks = uri.fragment?.takeIf(String::isNotBlank) ?: profile.name,
                address = uri.host.orEmpty(),
                port = uri.port.takeIf { it > 0 }?.toString() ?: "443",
                encryption = query("encryption", "none"),
                flow = query("flow", "none"),
                userId = uri.userInfo.orEmpty(),
                transport = query("type", query("net", "tcp")),
                headerType = query("headerType", "none"),
                path = query("path", ""),
                tlsType = query("security", "tls"),
                sni = query("sni", query("peer", "")),
                fingerprint = query("fp", "chrome"),
                alpn = query("alpn", "none"),
                publicKey = query("pbk", ""),
                shortId = query("sid", "")
            )
        }
    }

    fun toVlessLink(): String {
        val cleanAddress = address.trim().removePrefix("[").removeSuffix("]")
        require(cleanAddress.isNotBlank()) { "Address is required" }
        val parsedPort = port.trim().toIntOrNull()
        require(parsedPort != null && parsedPort in 1..65535) { "Enter a valid port from 1 to 65535" }
        require(userId.isNotBlank()) { "User ID is required" }

        val host = if (cleanAddress.contains(':')) "[$cleanAddress]" else cleanAddress
        val builder = Uri.Builder()
            .scheme("vless")
            .encodedAuthority("${Uri.encode(userId.trim())}@$host:${port.trim()}")
            .appendQueryParameter("encryption", encryption.ifBlank { "none" })
            .appendQueryParameter("security", tlsType.ifBlank { "none" })
            .appendQueryParameter("type", transport.ifBlank { "tcp" })

        if (flow.isNotBlank() && flow != "none") builder.appendQueryParameter("flow", flow.trim())
        if (headerType.isNotBlank() && headerType != "none") builder.appendQueryParameter("headerType", headerType.trim())
        if (sni.isNotBlank()) builder.appendQueryParameter("sni", sni.trim())
        if (fingerprint.isNotBlank() && fingerprint != "none") builder.appendQueryParameter("fp", fingerprint.trim())
        if (alpn.isNotBlank() && alpn != "none") builder.appendQueryParameter("alpn", alpn.trim())
        if (tlsType == "reality") {
            if (publicKey.isNotBlank()) builder.appendQueryParameter("pbk", publicKey.trim())
            if (shortId.isNotBlank()) builder.appendQueryParameter("sid", shortId.trim())
        }
        if (path.isNotBlank()) builder.appendQueryParameter("path", path.trim())
        if (remarks.isNotBlank()) builder.fragment(remarks.trim())
        return builder.build().toString()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditVlessScreen(
    profile: Profile,
    saving: Boolean,
    error: String,
    onCancel: () -> Unit,
    onSave: (String) -> Unit
) {
    var draft by remember(profile.id, profile.link) { mutableStateOf(VlessEditDraft.from(profile)) }
    var formError by remember(profile.id, profile.link) { mutableStateOf("") }
    val textFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel, enabled = !saving, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_x),
                    contentDescription = "Cancel editing",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "Edit VLess",
                modifier = Modifier.weight(1f).padding(start = 16.dp),
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(
                onClick = {
                    runCatching { draft.toVlessLink() }
                        .onSuccess(onSave)
                        .onFailure { formError = it.message ?: "Enter valid profile details" }
                },
                enabled = !saving,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_check),
                    contentDescription = "Save profile",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        if (error.isNotBlank() || formError.isNotBlank()) {
            Text(
                text = error.ifBlank { formError },
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().imePadding(),
            userScrollEnabled = !saving,
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "remarks") {
                FilledProfileField("Remarks", draft.remarks, textFieldColors, onValueChange = { draft = draft.copy(remarks = it) })
            }
            item(key = "address_port") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledProfileField(
                        label = "Address",
                        value = draft.address,
                        colors = textFieldColors,
                        modifier = Modifier.weight(3f),
                        onValueChange = { draft = draft.copy(address = it) }
                    )
                    FilledProfileField(
                        label = "Port",
                        value = draft.port,
                        colors = textFieldColors,
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1.4f),
                        onValueChange = { draft = draft.copy(port = it.filter(Char::isDigit)) }
                    )
                }
            }
            item(key = "divider_connection") { FormDivider() }
            item(key = "encryption") {
                ProfileDropdown("Encrypt method", draft.encryption, listOf("none", "zero", "aes-128-gcm", "chacha20-poly1305")) {
                    draft = draft.copy(encryption = it)
                }
            }
            item(key = "flow") {
                ProfileDropdown("Flow", draft.flow, listOf("none", "xtls-rprx-vision", "xtls-rprx-vision-udp443")) {
                    draft = draft.copy(flow = it)
                }
            }
            item(key = "user_id") {
                FilledProfileField("User ID", draft.userId, textFieldColors, onValueChange = { draft = draft.copy(userId = it) })
            }
            item(key = "divider_transport") { FormDivider() }
            item(key = "transport") {
                ProfileDropdown("Transfer protocol", draft.transport, listOf("tcp", "ws", "grpc", "h2", "kcp")) {
                    draft = draft.copy(transport = it)
                }
            }
            item(key = "header_type") {
                ProfileDropdown("Header type", draft.headerType, listOf("none", "http")) {
                    draft = draft.copy(headerType = it)
                }
            }
            item(key = "path") {
                FilledProfileField("Path / Service name", draft.path, textFieldColors, onValueChange = { draft = draft.copy(path = it) })
            }
            item(key = "divider_security") { FormDivider() }
            item(key = "tls") {
                ProfileDropdown("TLS type", draft.tlsType, listOf("tls", "reality", "none")) {
                    draft = draft.copy(tlsType = it)
                }
            }
            if (draft.tlsType == "reality") {
                item(key = "reality_public_key") {
                    FilledProfileField("Reality Public Key", draft.publicKey, textFieldColors, onValueChange = { draft = draft.copy(publicKey = it) })
                }
                item(key = "reality_short_id") {
                    FilledProfileField("Short ID", draft.shortId, textFieldColors, onValueChange = { draft = draft.copy(shortId = it) })
                }
            }
            item(key = "sni") {
                FilledProfileField("Server name indication", draft.sni, textFieldColors, onValueChange = { draft = draft.copy(sni = it) })
            }
            item(key = "fingerprint") {
                ProfileDropdown("Fingerprint", draft.fingerprint, listOf("chrome", "firefox", "safari", "ios", "android", "random", "none")) {
                    draft = draft.copy(fingerprint = it)
                }
            }
            item(key = "alpn") {
                ProfileDropdown("ALPN", draft.alpn, listOf("none", "h2", "http/1.1", "h2,http/1.1")) {
                    draft = draft.copy(alpn = it)
                }
            }
            if (saving) {
                item(key = "saving") {
                    Text("Saving profile…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FilledProfileField(
    label: String,
    value: String,
    colors: androidx.compose.material3.TextFieldColors,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = colors
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember(label) { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (options + value).distinct().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun FormDivider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
