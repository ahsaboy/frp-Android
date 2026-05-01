package io.github.acedroidx.frp.config.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.acedroidx.frp.R
import io.github.acedroidx.frp.config.FieldSchema
import io.github.acedroidx.frp.config.FieldType
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun FieldRenderer(
    field: FieldSchema,
    value: Any?,
    onChange: (Any?) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (field.type) {
        FieldType.STRING -> StringField(field, value as? String ?: "", onChange, modifier)
        FieldType.INT -> IntField(field, (value as? Number)?.toInt(), onChange, modifier)
        FieldType.LONG -> LongField(field, (value as? Number)?.toLong(), onChange, modifier)
        FieldType.BOOL -> BoolField(field, value as? Boolean ?: false, onChange, modifier)
        FieldType.ENUM -> EnumField(field, value as? String ?: "", onChange, modifier)
        FieldType.STRING_LIST -> StringListField(field, (value as? List<*>)?.filterIsInstance<String>() ?: emptyList(), onChange, modifier)
        FieldType.MAP_STRING -> MapField(field, (value as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value.toString() } ?: emptyMap(), onChange, modifier)
        FieldType.OBJECT -> {} // Handled by parent (SectionRenderer or PluginEditor)
    }
}

@Composable
private fun StringField(
    field: FieldSchema,
    value: String,
    onChange: (Any?) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = { onChange(it) },
        label = field.label,
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun IntField(
    field: FieldSchema,
    value: Int?,
    onChange: (Any?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var textValue by remember(value) { mutableStateOf(value?.toString() ?: "") }
    TextField(
        value = textValue,
        onValueChange = {
            textValue = it
            val parsed = it.toIntOrNull()
            if (parsed != null) onChange(parsed) else if (it.isEmpty()) onChange(null)
        },
        label = field.label,
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun LongField(
    field: FieldSchema,
    value: Long?,
    onChange: (Any?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var textValue by remember(value) { mutableStateOf(value?.toString() ?: "") }
    TextField(
        value = textValue,
        onValueChange = {
            textValue = it
            val parsed = it.toLongOrNull()
            if (parsed != null) onChange(parsed) else if (it.isEmpty()) onChange(null)
        },
        label = field.label,
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun BoolField(
    field: FieldSchema,
    value: Boolean,
    onChange: (Any?) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicComponent(
        modifier = modifier,
        title = field.label,
        onClick = { onChange(!value) },
        endActions = {
            Switch(checked = value, onCheckedChange = { onChange(it) })
        },
    )
}

@Composable
private fun EnumField(
    field: FieldSchema,
    value: String,
    onChange: (Any?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = value.ifEmpty { field.defaultValue?.toString() ?: "" }
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = field.label,
                style = MiuixTheme.textStyles.body1,
            )
            Text(
                text = displayValue,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.primary,
            )
        }
        OverlayListPopup(show = expanded, onDismissRequest = { expanded = false }) {
            ListPopupColumn {
                field.enumOptions.forEachIndexed { index, option ->
                    DropdownImpl(
                        text = option,
                        optionSize = field.enumOptions.size,
                        isSelected = value == option,
                        index = index,
                        onSelectedIndexChange = {
                            onChange(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StringListField(
    field: FieldSchema,
    value: List<String>,
    onChange: (Any?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(field.label, style = MiuixTheme.textStyles.body2, modifier = Modifier.padding(bottom = 4.dp))
        for ((index, item) in value.withIndex()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = item,
                    onValueChange = { newVal ->
                        val newList = value.toMutableList()
                        newList[index] = newVal
                        onChange(newList)
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    val newList = value.toMutableList()
                    newList.removeAt(index)
                    onChange(newList)
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_delete_24),
                        contentDescription = stringResource(R.string.delete_item)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        IconButton(onClick = {
            val newList = value.toMutableList()
            newList.add("")
            onChange(newList)
        }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_add_24dp),
                contentDescription = stringResource(R.string.add_item)
            )
        }
    }
}

@Composable
private fun MapField(
    field: FieldSchema,
    value: Map<String, String>,
    onChange: (Any?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(field.label, style = MiuixTheme.textStyles.body2, modifier = Modifier.padding(bottom = 4.dp))
        for ((key, v) in value) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = key,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = stringResource(R.string.field_key_label),
                    modifier = Modifier.weight(0.4f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextField(
                    value = v,
                    onValueChange = { newVal ->
                        val newMap = value.toMutableMap()
                        newMap[key] = newVal
                        onChange(newMap)
                    },
                    singleLine = true,
                    label = stringResource(R.string.field_value_label),
                    modifier = Modifier.weight(0.5f),
                )
                IconButton(onClick = {
                    val newMap = value.toMutableMap()
                    newMap.remove(key)
                    onChange(newMap)
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_delete_24),
                        contentDescription = stringResource(R.string.delete_item)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        AddMapEntryButton { newKey, newValue ->
            val newMap = value.toMutableMap()
            newMap[newKey] = newValue
            onChange(newMap)
        }
    }
}

@Composable
private fun AddMapEntryButton(onAdd: (String, String) -> Unit) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = key,
            onValueChange = { key = it },
            singleLine = true,
            label = stringResource(R.string.field_key_label),
            modifier = Modifier.weight(0.4f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        TextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            label = stringResource(R.string.field_value_label),
            modifier = Modifier.weight(0.4f),
        )
        IconButton(onClick = {
            if (key.isNotEmpty()) {
                onAdd(key, value)
                key = ""
                value = ""
            }
        }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_add_24dp),
                contentDescription = stringResource(R.string.add_item)
            )
        }
    }
}
