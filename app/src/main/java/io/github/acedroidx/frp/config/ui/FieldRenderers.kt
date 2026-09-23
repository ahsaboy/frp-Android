package io.github.acedroidx.frp.config.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.acedroidx.frp.R
import io.github.acedroidx.frp.config.FieldSchema
import io.github.acedroidx.frp.config.FieldType
import io.github.acedroidx.frp.config.SchemaHelpers
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
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
        FieldType.BOOL -> BoolField(field, value, onChange, modifier)
        FieldType.ENUM -> EnumField(field, value as? String ?: "", onChange, modifier)
        FieldType.STRING_LIST -> StringListField(field, (value as? List<*>)?.filterIsInstance<String>() ?: emptyList(), onChange, modifier)
        FieldType.MAP_STRING -> MapField(field, (value as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value.toString() } ?: emptyMap(), onChange, modifier)
        FieldType.MAP_BOOL -> BoolMapField(field, (value as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value as? Boolean ?: false } ?: emptyMap(), onChange, modifier)
        FieldType.OBJECT -> {} // Handled by parent (SectionRenderer or PluginEditor)
        FieldType.OBJECT_LIST -> ObjectListField(field, value, onChange, modifier)
    }
}

@Composable
private fun FieldFootnote(
    field: FieldSchema,
    showDefault: Boolean,
    modifier: Modifier = Modifier,
) {
    val defaultText = if (showDefault && field.defaultValue != null) {
        stringResource(R.string.field_uses_default, field.defaultValue.toString())
    } else {
        ""
    }
    val text = listOf(field.hint, defaultText).filter { it.isNotEmpty() }.joinToString("  ·  ")
    if (text.isNotEmpty()) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
            modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun StringField(
    field: FieldSchema,
    value: String,
    onChange: (Any?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = value,
            //清空 = 未设置：传 null，由数据层移除该键
            onValueChange = { onChange(if (it.isEmpty()) null else it) },
            label = field.label,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FieldFootnote(field, showDefault = value.isEmpty())
    }
}

@Composable
private fun IntField(
    field: FieldSchema,
    value: Int?,
    onChange: (Any?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var textValue by remember(value) { mutableStateOf(value?.toString() ?: "") }
    Column(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = textValue,
            onValueChange = {
                textValue = it
                val parsed = it.toIntOrNull()
                if (parsed != null) onChange(parsed) else if (it.isEmpty()) onChange(null)
            },
            label = field.label,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        FieldFootnote(field, showDefault = value == null)
    }
}

@Composable
private fun LongField(
    field: FieldSchema,
    value: Long?,
    onChange: (Any?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var textValue by remember(value) { mutableStateOf(value?.toString() ?: "") }
    Column(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = textValue,
            onValueChange = {
                textValue = it
                val parsed = it.toLongOrNull()
                if (parsed != null) onChange(parsed) else if (it.isEmpty()) onChange(null)
            },
            label = field.label,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        FieldFootnote(field, showDefault = value == null)
    }
}

@Composable
private fun BoolField(
    field: FieldSchema,
    value: Any?,
    onChange: (Any?) -> Unit,
    modifier: Modifier = Modifier,
) {
    //开关必须展示一个状态：未设置时回显 schema 默认值，拨动后才写入显式值
    val checked = (value as? Boolean) ?: (field.defaultValue as? Boolean) ?: false
    Column(modifier = modifier.fillMaxWidth()) {
        SwitchPreference(
            modifier = Modifier.fillMaxWidth(),
            title = field.label,
            checked = checked,
            onCheckedChange = { onChange(it) },
        )
        FieldFootnote(field, showDefault = value == null)
    }
}

@Composable
private fun EnumField(
    field: FieldSchema,
    value: String,
    onChange: (Any?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unsetLabel = stringResource(R.string.field_unset)
    // 历史上用空字符串表达“未设置”的选项，统一由未设置项承载
    val configured = field.enumOptions.filter { it.isNotEmpty() }.ifEmpty {
        listOfNotNull(field.defaultValue?.toString()?.takeIf { it.isNotEmpty() })
    }
    val allowsUnset = !field.required
    //保留不在选项列表中的历史值，避免静默丢失用户配置
    val unknownValue = value.takeIf { it.isNotEmpty() && it !in configured }
    val entries: List<String?> =
        (if (allowsUnset) listOf(null) else emptyList()) +
            configured +
            (if (unknownValue != null) listOf(unknownValue) else emptyList())
    val selectedIndex = if (value.isEmpty() && allowsUnset) 0 else entries.indexOf(value)
    Column(modifier = modifier.fillMaxWidth()) {
        OverlayDropdownPreference(
            modifier = Modifier.fillMaxWidth(),
            title = field.label,
            items = entries.map { it ?: unsetLabel },
            selectedIndex = selectedIndex,
            //选中“未设置”则传 null，由数据层移除该键
            onSelectedIndexChange = { index -> onChange(entries[index]) },
        )
        FieldFootnote(field, showDefault = value.isEmpty())
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
        FieldFootnote(field, showDefault = false)
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
                        imageVector = MiuixIcons.Delete,
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
                imageVector = MiuixIcons.Add,
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
        FieldFootnote(field, showDefault = false)
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
                        //空值 = 未设置：直接移除该条目
                        if (newVal.isEmpty()) newMap.remove(key) else newMap[key] = newVal
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
                        imageVector = MiuixIcons.Delete,
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
private fun BoolMapField(
    field: FieldSchema,
    value: Map<String, Boolean>,
    onChange: (Any?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(field.label, style = MiuixTheme.textStyles.body2, modifier = Modifier.padding(bottom = 4.dp))
        FieldFootnote(field, showDefault = false)
        for ((key, enabled) in value) {
            SwitchPreference(
                title = key,
                checked = enabled,
                onCheckedChange = { checked ->
                    val updated = value.toMutableMap()
                    updated[key] = checked
                    onChange(updated)
                },
                endActions = {
                    IconButton(onClick = {
                        val updated = value.toMutableMap()
                        updated.remove(key)
                        onChange(updated)
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = stringResource(R.string.delete_item),
                        )
                    }
                },
            )
        }
        AddMapBoolEntryButton { key ->
            val updated = value.toMutableMap()
            updated[key] = true
            onChange(updated)
        }
    }
}

@Composable
private fun AddMapBoolEntryButton(onAdd: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = key,
            onValueChange = { key = it },
            singleLine = true,
            label = stringResource(R.string.field_key_label),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = {
            if (key.isNotEmpty()) {
                onAdd(key)
                key = ""
            }
        }) {
            Icon(
                imageVector = MiuixIcons.Add,
                contentDescription = stringResource(R.string.add_item),
            )
        }
    }
}

@Composable
private fun ObjectListField(
    field: FieldSchema,
    rawValue: Any?,
    onChange: (Any?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val value = (rawValue as? List<*>)?.mapNotNull { item ->
        (item as? Map<*, *>)?.mapKeys { it.key.toString() }?.toMutableMap()
    } ?: emptyList()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(field.label, style = MiuixTheme.textStyles.body2, modifier = Modifier.padding(bottom = 4.dp))
        FieldFootnote(field, showDefault = false)
        for ((index, item) in value.withIndex()) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("#${index + 1}", style = MiuixTheme.textStyles.title3)
                        IconButton(onClick = {
                            onChange(value.toMutableList().also { it.removeAt(index) })
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Delete,
                                contentDescription = stringResource(R.string.delete_item),
                            )
                        }
                    }
                    for (child in field.children) {
                        val childValue = SchemaHelpers.getValueByPath(item, child.key)
                        FieldRenderer(
                            field = child,
                            value = childValue,
                            onChange = { newValue ->
                                val updatedItem = item.toMutableMap()
                                SchemaHelpers.setValueByPath(updatedItem, child.key, newValue)
                                onChange(value.toMutableList().also { it[index] = updatedItem })
                            },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
        IconButton(onClick = {
            onChange(value + mutableMapOf<String, Any?>())
        }) {
            Icon(
                imageVector = MiuixIcons.Add,
                contentDescription = stringResource(R.string.add_item),
            )
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
            if (key.isNotEmpty() && value.isNotEmpty()) {
                onAdd(key, value)
                key = ""
                value = ""
            }
        }) {
            Icon(
                imageVector = MiuixIcons.Add,
                contentDescription = stringResource(R.string.add_item)
            )
        }
    }
}
