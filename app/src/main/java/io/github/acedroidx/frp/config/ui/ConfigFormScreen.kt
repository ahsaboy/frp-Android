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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.acedroidx.frp.FrpType
import io.github.acedroidx.frp.R
import io.github.acedroidx.frp.config.ConfigFormViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    isAutoStartOnAppLaunch: Boolean,
    onAutoStartOnAppLaunchChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = remember { ConfigFormViewModel.create(configType, initialToml) }
    val isFormMode by viewModel.isFormMode.collectAsStateWithLifecycle()
    val textContent by viewModel.textContent.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(
                    if (configType == FrpType.FRPC) {
                        R.string.config_form_frpc_title
                    } else {
                        R.string.config_form_frps_title
                    }
                ),
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back_24dp),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        text = stringResource(
                            if (isFormMode) {
                                R.string.config_form_text_mode
                            } else {
                                R.string.config_form_form_mode
                            }
                        ),
                        onClick = {
                            if (isFormMode) viewModel.switchToTextMode()
                            else viewModel.switchToFormMode()
                        },
                    )
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
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.saveConfigButton))
                }
                TextButton(
                    text = stringResource(R.string.dontSaveConfigButton),
                    onClick = onDontSave,
                    modifier = Modifier.weight(1f),
                )
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
                    isAutoStartOnAppLaunch = isAutoStartOnAppLaunch,
                    onAutoStartOnAppLaunchChange = onAutoStartOnAppLaunchChange,
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
    isAutoStartOnAppLaunch: Boolean,
    onAutoStartOnAppLaunchChange: (Boolean) -> Unit,
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
        TabRow(
            tabs = tabs,
            selectedTabIndex = currentSection.coerceIn(0, tabs.size - 1),
            onTabSelected = { index ->
                viewModel.setSection(index)
                coroutineScope.launch { pagerState.animateScrollToPage(index) }
            },
            minWidth = 110.dp,
            maxWidth = 150.dp,
        )

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
                                isAutoStartOnAppLaunch = isAutoStartOnAppLaunch,
                                onAutoStartOnAppLaunchChange = onAutoStartOnAppLaunchChange,
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
    TextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier.fillMaxSize().padding(16.dp),
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
    )
}

@Composable
private fun ManagementSectionCard(
    configFileName: String,
    isAutoStart: Boolean,
    onAutoStartChange: (Boolean) -> Unit,
    isAutoStartOnAppLaunch: Boolean,
    onAutoStartOnAppLaunchChange: (Boolean) -> Unit,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRenameDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        cornerRadius = 16.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.config_management_title),
                style = MiuixTheme.textStyles.title2,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.config_file_name), style = MiuixTheme.textStyles.body1)
                TextButton(
                    text = configFileName,
                    onClick = { showRenameDialog = true },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.auto_start_switch), style = MiuixTheme.textStyles.body1)
                Switch(checked = isAutoStart, onCheckedChange = onAutoStartChange)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.auto_start_on_app_launch), style = MiuixTheme.textStyles.body1)
                Switch(checked = isAutoStartOnAppLaunch, onCheckedChange = onAutoStartOnAppLaunchChange)
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

@Composable
private fun RenameDialogInForm(
    originName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(originName) }
    OverlayDialog(
        show = true,
        title = stringResource(R.string.rename),
        onDismissRequest = onDismiss,
        content = {
            Column {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    label = stringResource(R.string.rename),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        text = stringResource(R.string.dismiss),
                        onClick = onDismiss,
                    )
                    TextButton(
                        text = stringResource(R.string.confirm),
                        onClick = { onConfirm("$text.toml") },
                    )
                }
            }
        },
    )
}
