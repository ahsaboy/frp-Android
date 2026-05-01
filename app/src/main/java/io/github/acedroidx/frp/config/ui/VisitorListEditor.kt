package io.github.acedroidx.frp.config.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.acedroidx.frp.config.ConfigFormViewModel
import io.github.acedroidx.frp.config.SchemaHelpers

@Composable
fun VisitorListEditor(
    viewModel: ConfigFormViewModel,
    modifier: Modifier = Modifier,
) {
    val visitors by viewModel.formData.visitors.collectAsStateWithLifecycle()
    val editingIndex by viewModel.editingVisitorIndex.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("访客列表", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))

        for ((index, visitor) in visitors.withIndex()) {
            VisitorCard(
                visitor = visitor,
                index = index,
                expanded = editingIndex == index,
                onToggle = {
                    viewModel.setEditingVisitor(if (editingIndex == index) null else index)
                },
                onDelete = { viewModel.deleteVisitor(index) },
                onUpdateField = { path, value -> viewModel.updateVisitorField(index, path, value) },
                viewModel = viewModel,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        TextButton(onClick = { viewModel.showAddVisitorDialog() }) {
            Text("+ 添加访客")
        }
    }

    val showAddDialog by viewModel.showAddVisitorDialog.collectAsStateWithLifecycle()
    if (showAddDialog) {
        AddVisitorTypeDialog(
            types = viewModel.configSchema.visitorTypes.map { it.type to it.label },
            onSelect = { viewModel.addVisitor(it) },
            onDismiss = { viewModel.hideAddVisitorDialog() },
        )
    }
}

@Composable
private fun VisitorCard(
    visitor: Map<String, Any?>,
    index: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onUpdateField: (String, Any?) -> Unit,
    viewModel: ConfigFormViewModel,
) {
    val visitorType = visitor["type"] as? String ?: ""
    val visitorName = visitor["name"] as? String ?: "(未命名)"
    val typeSchema = viewModel.getVisitorTypeSchema(visitorType)

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
                    Text(visitorName, style = MaterialTheme.typography.titleSmall)
                    Text(visitorType.uppercase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
                            val visible = field.visibleWhen?.invoke(visitor) ?: true
                            if (visible) {
                                val value = SchemaHelpers.getValueByPath(visitor, field.key)
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

@Composable
fun AddVisitorTypeDialog(
    types: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择访客类型") },
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
