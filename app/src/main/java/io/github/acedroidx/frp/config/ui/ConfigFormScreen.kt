package io.github.acedroidx.frp.config.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.acedroidx.frp.FrpType
import io.github.acedroidx.frp.R
import io.github.acedroidx.frp.config.ConfigFormViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigFormScreen(
    configType: FrpType,
    initialToml: String,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
    onDontSave: () -> Unit,
    configFileName: String,
    onRename: (String) -> Unit,
    isAutoStart: Boolean,
    onAutoStartChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = remember { ConfigFormViewModel.create(configType, initialToml) }
    val isFormMode by viewModel.isFormMode.collectAsStateWithLifecycle()
    val textContent by viewModel.textContent.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (configType == FrpType.FRPC) "frpc 配置" else "frps 配置")
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (isFormMode) viewModel.switchToTextMode()
                        else viewModel.switchToFormMode()
                    }) {
                        Text(if (isFormMode) "文本模式" else "表单模式")
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        if (isFormMode) onSave(viewModel.formData.toToml())
                        else onSave(textContent)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.saveConfigButton))
                }
                OutlinedButton(
                    onClick = onDontSave,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.dontSaveConfigButton))
                }
            }
        },
        modifier = modifier,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isFormMode) {
                FormModeContent(
                    viewModel = viewModel,
                    configType = configType,
                    configFileName = configFileName,
                    isAutoStart = isAutoStart,
                    onAutoStartChange = onAutoStartChange,
                    onRename = onRename,
                )
            } else {
                TextModeContent(textContent) { viewModel.setTextContent(it) }
            }
        }
    }
}

@Composable
private fun FormModeContent(
    viewModel: ConfigFormViewModel,
    configType: FrpType,
    configFileName: String,
    isAutoStart: Boolean,
    onAutoStartChange: (Boolean) -> Unit,
    onRename: (String) -> Unit,
) {
    val schema = viewModel.configSchema
    val currentSection by viewModel.currentSection.collectAsStateWithLifecycle()
    val expandedSections by viewModel.expandedSections.collectAsStateWithLifecycle()

    val tabs = schema.sections.map { it.title } +
        if (configType == FrpType.FRPC) listOf("代理", "访客") else emptyList()

    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    LaunchedEffect(currentSection) {
        if (pagerState.currentPage != currentSection) {
            pagerState.animateScrollToPage(currentSection)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (viewModel.currentSection.value != pagerState.currentPage) {
            viewModel.setSection(pagerState.currentPage)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = currentSection.coerceIn(0, tabs.size - 1),
            edgePadding = 0.dp,
        ) {
            for ((index, title) in tabs.withIndex()) {
                Tab(
                    selected = currentSection == index,
                    onClick = {
                        viewModel.setSection(index)
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    },
                    text = { Text(title, maxLines = 1) },
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (page < schema.sections.size) {
                    val section = schema.sections[page]
                    if (page == 0) {
                        item(key = "_management") {
                            ManagementSectionCard(
                                configFileName = configFileName,
                                isAutoStart = isAutoStart,
                                onAutoStartChange = onAutoStartChange,
                                onRename = onRename,
                            )
                        }
                    }
                    item(key = section.id) {
                        SectionCard(
                            section = section,
                            formData = viewModel.formData,
                            expanded = expandedSections.contains(section.id),
                            onToggle = { viewModel.toggleSection(section.id) },
                        )
                    }
                } else if (configType == FrpType.FRPC) {
                    val sectionOffset = schema.sections.size
                    when (page - sectionOffset) {
                        0 -> item { ProxyListEditor(viewModel) }
                        1 -> item { VisitorListEditor(viewModel) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextModeContent(
    text: String,
    onTextChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier.fillMaxSize().padding(16.dp),
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
    )
}

@Composable
private fun ManagementSectionCard(
    configFileName: String,
    isAutoStart: Boolean,
    onAutoStartChange: (Boolean) -> Unit,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRenameDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "文件管理",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("配置文件名", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { showRenameDialog = true }) {
                    Text(configFileName)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.auto_start_switch), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = isAutoStart, onCheckedChange = onAutoStartChange)
            }
        }
    }

    if (showRenameDialog) {
        RenameDialogInForm(
            originName = configFileName,
            onConfirm = { newName ->
                onRename(newName)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameDialogInForm(
    originName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(originName) }
    AlertDialog(
        title = { Text(stringResource(R.string.rename)) },
        icon = {
            Icon(painterResource(id = R.drawable.ic_rename), contentDescription = "Rename Icon")
        },
        text = { TextField(text, onValueChange = { text = it }) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm("$text.toml") }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss))
            }
        },
    )
}
