package io.github.acedroidx.frp.config.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import io.github.acedroidx.frp.config.ConfigFormData
import io.github.acedroidx.frp.config.ConfigSection
import io.github.acedroidx.frp.config.FieldType
import io.github.acedroidx.frp.config.SchemaHelpers
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SectionCard(
    section: ConfigSection,
    formData: ConfigFormData,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val values by formData.values.collectAsStateWithLifecycle()

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = section.title,
                    style = MiuixTheme.textStyles.title2,
                    modifier = Modifier.weight(1f),
                )
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
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    for (field in section.fields) {
                        val visible = field.visibleWhen?.invoke(values) ?: true
                        if (visible) {
                            val currentValue = SchemaHelpers.getValueByPath(values, field.key)
                            if (field.type == FieldType.OBJECT) {
                                // Object fields are rendered by their children in a sub-section
                                ObjectSubSection(field, formData)
                            } else {
                                FieldRenderer(
                                    field = field,
                                    value = currentValue,
                                    onChange = { newValue -> formData.setValue(field.key, newValue) },
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
private fun ObjectSubSection(
    field: io.github.acedroidx.frp.config.FieldSchema,
    formData: ConfigFormData,
) {
    val values by formData.values.collectAsStateWithLifecycle()
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = field.label,
            style = MiuixTheme.textStyles.title3,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        for (child in field.children) {
            val visible = child.visibleWhen?.invoke(values) ?: true
            if (visible) {
                val currentValue = SchemaHelpers.getValueByPath(values, child.key)
                FieldRenderer(
                    field = child,
                    value = currentValue,
                    onChange = { newValue -> formData.setValue(child.key, newValue) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
