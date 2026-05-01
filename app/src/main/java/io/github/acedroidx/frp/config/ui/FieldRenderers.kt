package io.github.acedroidx.frp.config.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.acedroidx.frp.config.FieldSchema
import io.github.acedroidx.frp.config.FieldType

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
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it) },
        label = { Text(field.label) },
        placeholder = { if (field.hint.isNotEmpty()) Text(field.hint) },
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
    OutlinedTextField(
        value = textValue,
        onValueChange = {
            textValue = it
            val parsed = it.toIntOrNull()
            if (parsed != null) onChange(parsed) else if (it.isEmpty()) onChange(null)
        },
        label = { Text(field.label) },
        placeholder = { if (field.hint.isNotEmpty()) Text(field.hint) },
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
    OutlinedTextField(
        value = textValue,
        onValueChange = {
            textValue = it
            val parsed = it.toLongOrNull()
            if (parsed != null) onChange(parsed) else if (it.isEmpty()) onChange(null)
        },
        label = { Text(field.label) },
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
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(field.label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = value, onCheckedChange = { onChange(it) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumField(
    field: FieldSchema,
    value: String,
    onChange: (Any?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value.ifEmpty { field.defaultValue?.toString() ?: "" },
            onValueChange = {},
            readOnly = true,
            label = { Text(field.label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (option in field.enumOptions) {
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onChange(option)
                        expanded = false
                    },
                )
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
        Text(field.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
        for ((index, item) in value.withIndex()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
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
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        IconButton(onClick = {
            val newList = value.toMutableList()
            newList.add("")
            onChange(newList)
        }) {
            Icon(Icons.Default.Add, contentDescription = "添加")
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
        Text(field.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
        for ((key, v) in value) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = key,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text("Key") },
                    modifier = Modifier.weight(0.4f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = v,
                    onValueChange = { newVal ->
                        val newMap = value.toMutableMap()
                        newMap[key] = newVal
                        onChange(newMap)
                    },
                    singleLine = true,
                    label = { Text("Value") },
                    modifier = Modifier.weight(0.5f),
                )
                IconButton(onClick = {
                    val newMap = value.toMutableMap()
                    newMap.remove(key)
                    onChange(newMap)
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
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
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            singleLine = true,
            label = { Text("Key") },
            modifier = Modifier.weight(0.4f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            label = { Text("Value") },
            modifier = Modifier.weight(0.4f),
        )
        IconButton(onClick = {
            if (key.isNotEmpty()) {
                onAdd(key, value)
                key = ""
                value = ""
            }
        }) {
            Icon(Icons.Default.Add, contentDescription = "添加")
        }
    }
}
