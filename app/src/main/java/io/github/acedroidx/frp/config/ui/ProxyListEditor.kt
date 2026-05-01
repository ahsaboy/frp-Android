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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.acedroidx.frp.R
import io.github.acedroidx.frp.config.ConfigFormViewModel
import io.github.acedroidx.frp.config.FieldType
import io.github.acedroidx.frp.config.SchemaHelpers
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ProxyListEditor(
    viewModel: ConfigFormViewModel,
    modifier: Modifier = Modifier,
) {
    val proxies by viewModel.formData.proxies.collectAsStateWithLifecycle()
    val editingIndex by viewModel.editingProxyIndex.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            stringResource(R.string.proxy_list_title),
            style = MiuixTheme.textStyles.title2,
            modifier = Modifier.padding(bottom = 8.dp)
        )

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

        TextButton(text = stringResource(R.string.proxy_add), onClick = { viewModel.showAddProxyDialog() })
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
    val proxyName = proxy["name"] as? String ?: stringResource(R.string.config_item_unnamed)
    val typeSchema = viewModel.getProxyTypeSchema(proxyType)

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(proxyName, style = MiuixTheme.textStyles.title3)
                    Text(proxyType.uppercase(), style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_delete_24),
                        contentDescription = stringResource(R.string.delete_item)
                    )
                }
                Icon(
                    painter = painterResource(
                        if (expanded) R.drawable.ic_expand_less_24dp else R.drawable.ic_expand_more_24dp
                    ),
                    contentDescription = stringResource(
                        if (expanded) R.string.collapse else R.string.expand
                    ),
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
        Text(
            stringResource(R.string.plugin_title),
            style = MiuixTheme.textStyles.title3,
            modifier = Modifier.padding(bottom = 8.dp)
        )

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

@Composable
private fun PluginTypeSelector(
    selectedType: String,
    types: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val allOptions = listOf(stringResource(R.string.no_plugin) to "") + types
    val displayText = allOptions.find { it.second == selectedType }?.first ?: selectedType

    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = displayText,
            onValueChange = {},
            label = stringResource(R.string.plugin_type_label),
            readOnly = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
        )
        OverlayListPopup(
            show = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ListPopupColumn {
                allOptions.forEachIndexed { index, (label, type) ->
                    DropdownImpl(
                        text = label,
                        optionSize = allOptions.size,
                        isSelected = (if (type.isEmpty()) selectedType.isEmpty() else selectedType == type),
                        index = index,
                        onSelectedIndexChange = {
                            onSelect(type)
                            expanded = false
                        },
                    )
                }
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
    OverlayDialog(
        show = true,
        title = stringResource(R.string.proxy_type_dialog_title),
        onDismissRequest = onDismiss,
        content = {
            Column {
                for ((type, label) in types) {
                    TextButton(
                        text = label,
                        onClick = { onSelect(type) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(text = stringResource(R.string.dismiss), onClick = onDismiss)
                }
            }
        },
    )
}
