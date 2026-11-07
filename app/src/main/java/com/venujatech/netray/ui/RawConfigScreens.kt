package com.venujatech.netray.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venujatech.netray.R

@Composable
fun RawConfigDialog(
    remarks: String,
    onRemarksChange: (String) -> Unit,
    error: String,
    onOpenEditor: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.raw_config_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TextField(
                    value = remarks,
                    onValueChange = onRemarksChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_remarks)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                TextButton(
                    onClick = onOpenEditor,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        containerColor = Color(0xFFF44336),
                        contentColor = Color.White
                    )
                ) {
                    Text(stringResource(R.string.action_open_text_editor), fontSize = 16.sp)
                }
                Text(
                    stringResource(R.string.raw_config_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun RawJsonEditorScreen(
    initialJson: String,
    error: String,
    onCancel: () -> Unit,
    onPaste: () -> Unit,
    onSave: (String) -> Unit
) {
    var json by rememberSaveable(initialJson) { mutableStateOf(initialJson) }
    var localError by rememberSaveable(initialJson) { mutableStateOf("") }
    val invalidJsonMessage = stringResource(R.string.error_invalid_json)
    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_lucide_x), contentDescription = stringResource(R.string.action_cancel), tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(stringResource(R.string.raw_editor_title), modifier = Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onPaste) { Text(stringResource(R.string.action_paste)) }
            IconButton(
                onClick = {
                    val invalid = runCatching { org.json.JSONObject(json) }.isFailure
                    if (invalid) localError = invalidJsonMessage
                    else { localError = ""; onSave(json) }
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(painterResource(R.drawable.ic_lucide_check), contentDescription = stringResource(R.string.action_save), tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (error.isNotBlank() || localError.isNotBlank()) {
            Text(error.ifBlank { localError }, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }
        TextField(
            value = json,
            onValueChange = { json = it; localError = "" },
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("{\n  \"outbounds\": []\n}") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
    }
}
