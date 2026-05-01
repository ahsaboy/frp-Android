package io.github.acedroidx.frp.config.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.acedroidx.frp.config.ConfigFormViewModel
import io.github.acedroidx.frp.config.FieldType
import io.github.acedroidx.frp.config.SchemaHelpers

@Composable
fun ProxyListEditor(
    viewModel: ConfigFormViewModel,
    modifier: Modifier = Modifier,
) {
    val proxies by viewModel.formData.proxies.collectAsStateWithLifecycle()
    val editingIndex by viewModel.editingProxyIndex.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("代理列表", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))

        for ((index, proxy) in proxies.withIndex()) {
            ProxyCard(
                proxy = proxy,
                index = index,
                expanded = editingIndex == index,
                onToggle = {
                    viewModel.setEditingProxy(if (editingIndex == index) null else index)
                },
                onDelete = { viewModel.deleteProxy(index) },
                onUpdateField = { path, value -> viewModel.updateProxyField(index, path, value) },
                viewModel = viewModel,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        TextButton(onClick = { viewModel.showAddProxyDialog() }) {
            Text("+ 添加代理")
        }
    }

    val showAddDialog by viewModel.showAddProxyDialog.collectAsStateWithLifecycle()
    if (showAddDialog) {
        AddProxyTypeDialog(
            types = viewModel.configSchema.proxyTypes.map { it.type to it.label },
            onSelect = { viewModel.addProxy(it) },
            onDismiss = { viewModel.hideAddProxyDialog() },
        )
    }
}

@Composable
private fun ProxyCard(
    proxy: Map<String, Any?>,
    index: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onUpdateField: (String, Any?) -> Unit,
    viewModel: ConfigFormViewModel,
) {
    val proxyType = proxy["type"] as? String ?: ""
    val proxyName = proxy["name"] as? String ?: "(未命名)"
    val typeSchema = viewModel.getProxyTypeSchema(proxyType)

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(proxyName, style = MaterialTheme.typography.titleSmall)
                    Text(proxyType.uppercase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    if (typeSchema != null) {
                        val allFields = typeSchema.baseFields + typeSchema.typeSpecificFields
                        for (field in allFields) {
                            if (field.key == "type") continue
                            val visible = field.visibleWhen?.invoke(proxy) ?: true
                            if (visible) {
                                val value = SchemaHelpers.getValueByPath(proxy, field.key)
                                if (field.type == FieldType.OBJECT) {
                                    // Plugin or other nested object
                                    PluginSection(field, proxy, onUpdateField, viewModel)
                                } else {
                                    FieldRenderer(
                                        field = field,
                                        value = value,
                                        onChange = { onUpdateField(field.key, it) },
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginSection(
    field: io.github.acedroidx.frp.config.FieldSchema,
    proxy: Map<String, Any?>,
    onUpdateField: (String, Any?) -> Unit,
    viewModel: ConfigFormViewModel,
) {
    val pluginData = SchemaHelpers.getValueByPath(proxy, field.key) as? Map<*, *>
    val pluginType = pluginData?.get("type") as? String ?: ""

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("插件", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))

        // Plugin type selector
        PluginTypeSelector(
            selectedType = pluginType,
            types = viewModel.configSchema.pluginTypes.map { it.type to it.label },
            onSelect = { newType ->
                val newPlugin = mutableMapOf<String, Any?>("type" to newType)
                onUpdateField(field.key, newPlugin)
            },
        )

        if (pluginType.isNotEmpty()) {
            val pluginSchema = viewModel.getPluginTypeSchema(pluginType)
            if (pluginSchema != null) {
                for (pField in pluginSchema.fields) {
                    val pValue = pluginData?.let {
                        SchemaHelpers.getValueByPath(it as Map<String, Any?>, pField.key)
                    }
                    FieldRenderer(
                        field = pField,
                        value = pValue,
                        onChange = { newVal ->
                            val updatedPlugin: MutableMap<String, Any?> = (pluginData?.mapKeys { it.key.toString() }?.mapValues { it.value }?.toMutableMap() ?: mutableMapOf("type" to pluginType)).toMutableMap()
                            SchemaHelpers.setValueByPath(updatedPlugin, pField.key, newVal)
                            onUpdateField(field.key, updatedPlugin)
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginTypeSelector(
    selectedType: String,
    types: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = types.find { it.first == selectedType }?.second ?: selectedType

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text("插件类型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("(无)") },
                onClick = {
                    onSelect("")
                    expanded = false
                },
            )
            for ((type, label) in types) {
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun AddProxyTypeDialog(
    types: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择代理类型") },
        text = {
            Column {
                for ((type, label) in types) {
                    TextButton(
                        onClick = { onSelect(type) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
