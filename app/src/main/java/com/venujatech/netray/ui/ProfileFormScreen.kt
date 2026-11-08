package com.venujatech.netray.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.venujatech.netray.R
import com.venujatech.netray.profiles.ConfigDraft
import com.venujatech.netray.profiles.ConfigFieldKeys
import com.venujatech.netray.profiles.ConfigFieldKind
import com.venujatech.netray.profiles.ConfigFieldSpec
import com.venujatech.netray.profiles.ConfigParserRouter
import com.venujatech.netray.profiles.ConfigRowSpec
import com.venujatech.netray.profiles.Profile
import com.venujatech.netray.profiles.ProtocolFormSchema
import com.venujatech.netray.profiles.ProtocolFormSchemas
import com.venujatech.netray.profiles.ProtocolKind
import java.util.UUID

private val ConfigDraftSaver: Saver<ConfigDraft, Any> = listSaver<ConfigDraft, String>(
    save = { draft -> listOf(draft.protocol.key) + draft.fields.entries.flatMap { listOf(it.key, it.value) } },
    restore = { saved ->
        val protocol = ProtocolKind.fromKey(saved.firstOrNull().orEmpty()) ?: ProtocolKind.VLESS
        val fields = saved.drop(1).chunked(2).filter { it.size == 2 }.associate { it[0] to it[1] }
        ConfigDraft.create(protocol).withAll(fields)
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileFormScreen(
    protocol: ProtocolKind,
    existing: Profile?,
    initialDraft: ConfigDraft? = null,
    saving: Boolean,
    saveError: String,
    onDiscard: () -> Unit,
    onSave: (Profile) -> Unit
) {
    val schema = ProtocolFormSchemas[protocol] ?: return
    val initial = remember(protocol, existing?.id, existing?.link) {
        existing?.let { ConfigDraft(it.protocol, it.fields) } ?: initialDraft ?: ConfigDraft.create(protocol)
    }
    var draft by rememberSaveable(protocol.key, existing?.id, stateSaver = ConfigDraftSaver) { mutableStateOf(initial) }
    var fieldErrors by rememberSaveable(protocol.key, existing?.id, stateSaver = stringMapSaver) { mutableStateOf(emptyMap()) }
    var dirtyConfirm by remember { mutableStateOf(false) }
    var helpVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val colors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent
    )
    val dirty = draft.fields != initial.fields

    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (dirty) dirtyConfirm = true else onDiscard() }, enabled = !saving, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_lucide_x), contentDescription = stringResource(R.string.action_cancel), tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
            }
            Text(
                text = stringResource(if (existing == null) R.string.action_add_protocol else R.string.action_edit_protocol, stringResource(protocol.titleRes)),
                modifier = Modifier.weight(1f).padding(start = 16.dp),
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            if (schema.hasHelp) {
                IconButton(onClick = { helpVisible = true }, enabled = !saving, modifier = Modifier.size(48.dp)) {
                    Text("?", color = MaterialTheme.colorScheme.onBackground, fontSize = 22.sp)
                }
            }
            IconButton(
                onClick = {
                    val errors = validateDraft(schema, draft, context)
                    fieldErrors = errors
                    if (errors.isEmpty()) {
                        runCatching { Profile.fromDraft(draft, existing?.id ?: UUID.randomUUID().toString()) }
                            .onSuccess(onSave)
                            .onFailure { fieldErrors = mapOf(ConfigFieldKeys.RAW_JSON to it.message.orEmpty()) }
                    }
                },
                enabled = !saving,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(painterResource(R.drawable.ic_lucide_check), contentDescription = stringResource(R.string.action_save), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
        }

        if (saveError.isNotBlank()) Text(saveError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().imePadding(),
            userScrollEnabled = !saving,
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            schema.rows.forEachIndexed { rowIndex, row ->
                val visibleFields = row.fields.filter { field ->
                    field.visibleWhen == null || draft[field.visibleWhen.first] == field.visibleWhen.second
                }
                if (visibleFields.isNotEmpty()) {
                    item(key = "${protocol.key}_row_$rowIndex") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (row.dividerBefore) FormDivider()
                            if (visibleFields.size == 2) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    visibleFields.forEach { field ->
                                        FieldControl(field, draft, fieldErrors[field.key], colors, Modifier.weight(if (field.key == ConfigFieldKeys.PORT) 1.2f else 3f), saving) { updated -> draft = updated; fieldErrors = fieldErrors - field.key }
                                    }
                                }
                            } else {
                                visibleFields.forEach { field ->
                                    FieldControl(field, draft, fieldErrors[field.key], colors, Modifier.fillMaxWidth(), saving) { updated -> draft = updated; fieldErrors = fieldErrors - field.key }
                                }
                            }
                        }
                    }
                } else if (row.dividerBefore) {
                    item(key = "${protocol.key}_divider_$rowIndex") { FormDivider() }
                }
            }
            fieldErrors[ConfigFieldKeys.RAW_JSON]?.takeIf(String::isNotBlank)?.let { message ->
                item(key = "${protocol.key}_form_error") { Text(message, color = MaterialTheme.colorScheme.error) }
            }
            if (saving) item(key = "${protocol.key}_saving") { Text(stringResource(R.string.saving_profile), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }

    if (dirtyConfirm) AlertDialog(
        onDismissRequest = { dirtyConfirm = false },
        title = { Text(stringResource(R.string.discard_changes_title)) },
        text = { Text(stringResource(R.string.discard_changes_message)) },
        confirmButton = { TextButton(onClick = { dirtyConfirm = false; onDiscard() }) { Text(stringResource(R.string.action_discard)) } },
        dismissButton = { TextButton(onClick = { dirtyConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = MaterialTheme.colorScheme.surface
    )

    if (helpVisible) AlertDialog(
        onDismissRequest = { helpVisible = false },
        title = { Text(stringResource(R.string.ssh_config_reference_title)) },
        text = { Text(stringResource(R.string.ssh_config_reference_body)) },
        confirmButton = { TextButton(onClick = { helpVisible = false }) { Text(stringResource(R.string.action_close)) } },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldControl(
    field: ConfigFieldSpec,
    draft: ConfigDraft,
    error: String?,
    colors: androidx.compose.material3.TextFieldColors,
    modifier: Modifier,
    disabled: Boolean,
    onChange: (ConfigDraft) -> Unit
) {
    val value = draft[field.key]
    val label = stringResource(field.labelRes)
    val errorText = error.orEmpty()
    when (field.kind) {
        ConfigFieldKind.DROPDOWN -> ProfileDropdown(field, value, modifier, disabled, errorText) { onChange(draft.with(field.key, it)) }
        ConfigFieldKind.SWITCH -> Row(modifier = modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            androidx.compose.material3.Switch(checked = value == "true", enabled = !disabled, onCheckedChange = { onChange(draft.with(field.key, it.toString())) })
        }
        else -> {
            var visible by remember(field.key) { mutableStateOf(field.kind != ConfigFieldKind.PASSWORD) }
            val keyboard = when {
                field.key == ConfigFieldKeys.PORT || field.key.contains("interval") || field.kind == ConfigFieldKind.BANDWIDTH -> KeyboardType.Number
                else -> KeyboardType.Text
            }
            TextField(
                value = value,
                onValueChange = { onChange(draft.with(field.key, it)) },
                modifier = modifier.fillMaxWidth(),
                label = { Text(label) },
                placeholder = field.defaultValue.takeIf(String::isNotBlank)?.let { hint -> ({ Text(hint) }) },
                singleLine = field.kind != ConfigFieldKind.MULTILINE,
                minLines = if (field.kind == ConfigFieldKind.MULTILINE) 3 else 1,
                enabled = !disabled,
                isError = errorText.isNotBlank(),
                supportingText = errorText.takeIf(String::isNotBlank)?.let { ({ Text(it) }) },
                trailingIcon = if (field.kind == ConfigFieldKind.PASSWORD) ({ TextButton(onClick = { visible = !visible }) { Text(stringResource(if (visible) R.string.action_hide else R.string.action_show)) } }) else null,
                visualTransformation = if (field.kind == ConfigFieldKind.PASSWORD && !visible) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                shape = RoundedCornerShape(16.dp),
                colors = colors
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileDropdown(
    field: ConfigFieldSpec,
    value: String,
    modifier: Modifier,
    disabled: Boolean,
    error: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember(field.key) { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (!disabled) expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = !disabled,
            isError = error.isNotBlank(),
            label = { Text(stringResource(field.labelRes)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            supportingText = error.takeIf(String::isNotBlank)?.let { ({ Text(it) }) },
            shape = RoundedCornerShape(16.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (field.choices + value).distinct().forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelected(option); expanded = false })
            }
        }
    }
}

@Composable
private fun FormDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

private val stringMapSaver: Saver<Map<String, String>, Any> = listSaver<Map<String, String>, String>(
    save = { map -> map.entries.flatMap { listOf(it.key, it.value) } },
    restore = { values -> values.chunked(2).filter { it.size == 2 }.associate { it[0] to it[1] }
    }
)

private fun validateDraft(schema: ProtocolFormSchema, draft: ConfigDraft, context: android.content.Context): Map<String, String> {
    val errors = linkedMapOf<String, String>()
    schema.rows.flatMap { it.fields }.filter { it.visibleWhen == null || draft[it.visibleWhen.first] == it.visibleWhen.second }
        .filter { it.required && draft[it.key].isBlank() }
        .forEach { errors[it.key] = context.getString(R.string.error_required_field, context.getString(it.labelRes)) }
    if (draft.protocol != ProtocolKind.RAW_JSON && draft.protocol != ProtocolKind.DNSTT_SSH) {
        val port = draft[ConfigFieldKeys.PORT].toIntOrNull()
        if (port == null || port !in 1..65535) errors[ConfigFieldKeys.PORT] = context.getString(R.string.error_invalid_port)
    }
    if (draft.protocol == ProtocolKind.RAW_JSON) {
        runCatching { ConfigParserRouter.validate(draft) }.onFailure { errors[ConfigFieldKeys.RAW_JSON] = context.getString(R.string.error_invalid_json) }
    } else {
        runCatching { ConfigParserRouter.validate(draft) }.onFailure { exception ->
            val field = when {
                exception.message?.contains("Address") == true -> ConfigFieldKeys.ADDRESS
                exception.message?.contains("DNS address") == true -> ConfigFieldKeys.DNS_ADDRESS
                exception.message?.contains("Nameserver") == true -> ConfigFieldKeys.NAMESERVER
                exception.message?.contains("Port") == true -> ConfigFieldKeys.PORT
                else -> ConfigFieldKeys.REMARKS
            }
            errors.putIfAbsent(field, exception.message.orEmpty())
        }
    }
    return errors
}
