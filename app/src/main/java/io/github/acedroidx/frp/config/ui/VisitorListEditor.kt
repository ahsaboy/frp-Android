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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.acedroidx.frp.R
import io.github.acedroidx.frp.config.ConfigFormViewModel
import io.github.acedroidx.frp.config.SchemaHelpers
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun VisitorListEditor(
    viewModel: ConfigFormViewModel,
    modifier: Modifier = Modifier,
) {
    val visitors by viewModel.formData.visitors.collectAsStateWithLifecycle()
    val editingIndex by viewModel.editingVisitorIndex.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            stringResource(R.string.visitor_list_title),
            style = MiuixTheme.textStyles.title2,
            modifier = Modifier.padding(bottom = 8.dp)
        )

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

        TextButton(text = stringResource(R.string.visitor_add), onClick = { viewModel.showAddVisitorDialog() })
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
    val visitorName = visitor["name"] as? String ?: stringResource(R.string.config_item_unnamed)
    val typeSchema = viewModel.getVisitorTypeSchema(visitorType)

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(visitorName, style = MiuixTheme.textStyles.title3)
                    Text(visitorType.uppercase(), style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.primary)
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
    OverlayDialog(
        show = true,
        title = stringResource(R.string.visitor_type_dialog_title),
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
