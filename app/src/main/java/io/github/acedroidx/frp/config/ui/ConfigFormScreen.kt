package io.github.acedroidx.frp.config.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.acedroidx.frp.FrpType
import io.github.acedroidx.frp.R
import io.github.acedroidx.frp.config.ConfigFormViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.basic.TextButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConfigFormScreen(
    configType: FrpType,
    initialToml: String,
    onSave: (String) -> Unit,
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
    val textModeError by viewModel.textModeError.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collectLatest { page ->
            if (page == 1 && viewModel.isFormMode.value) {
                viewModel.switchToTextMode()
            } else if (page == 0 && !viewModel.isFormMode.value) {
                viewModel.switchToFormMode()
                if (!viewModel.isFormMode.value) pagerState.animateScrollToPage(1)
            }
        }
    }

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
                    IconButton(onClick = onDontSave) {
                        Icon(
                            imageVector = MiuixIcons.Close,
                            contentDescription = stringResource(R.string.dontSaveConfigButton),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (isFormMode) onSave(viewModel.formData.toToml())
                            else onSave(textContent)
                        },
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Ok,
                            contentDescription = stringResource(R.string.saveConfigButton),
                        )
                    }
                },
            )
        },
        bottomBar = {
            TabRow(
                tabs = listOf(
                    stringResource(R.string.nav_form_mode),
                    stringResource(R.string.nav_text_mode),
                ),
                selectedTabIndex = if (isFormMode) 0 else 1,
                onTabSelected = { index ->
                    coroutineScope.launch {
                        if (index == 0) {
                            viewModel.switchToFormMode()
                        } else {
                            viewModel.switchToTextMode()
                        }
                        pagerState.animateScrollToPage(if (viewModel.isFormMode.value) 0 else 1)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        },
        modifier = modifier,
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding),
            userScrollEnabled = false,
            beyondViewportPageCount = 1,
        ) { page ->
            if (page == 0) {
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
                TextModeContent(
                    text = textContent,
                    hasParseError = textModeError,
                    onTextChange = viewModel::setTextContent,
                )
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
    val allSchemaFields = remember(schema) { schema.sections.flatMap { it.fields } }
    val currentSection by viewModel.currentSection.collectAsStateWithLifecycle()
    val expandedSections by viewModel.expandedSections.collectAsStateWithLifecycle()

    val tabs = schema.sections.map { it.title } +
        if (configType == FrpType.FRPC) {
            listOf(
                stringResource(R.string.proxy_list_title),
                stringResource(R.string.visitor_list_title),
            )
        } else {
            emptyList()
        }

    val tabsVisible = remember { mutableStateOf(true) }
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (consumed.y < 0f) tabsVisible.value = false
                if (consumed.y > 0f) tabsVisible.value = true
                return Offset.Zero
            }
        }
    }
    val sectionPagerState = rememberPagerState(
        initialPage = currentSection.coerceIn(0, tabs.lastIndex),
        pageCount = { tabs.size },
    )
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(sectionPagerState) {
        snapshotFlow { sectionPagerState.settledPage }.collectLatest(viewModel::setSection)
    }
    LaunchedEffect(currentSection) {
        val page = currentSection.coerceIn(0, tabs.lastIndex)
        if (sectionPagerState.currentPage != page) sectionPagerState.animateScrollToPage(page)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollConnection),
    ) {
        AnimatedVisibility(
            visible = tabsVisible.value,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            TabRow(
                tabs = tabs,
                selectedTabIndex = sectionPagerState.currentPage,
                onTabSelected = { index ->
                    coroutineScope.launch { sectionPagerState.animateScrollToPage(index) }
                },
                minWidth = 110.dp,
                maxWidth = 150.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HorizontalPager(
            state = sectionPagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { selectedSection ->
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (selectedSection < schema.sections.size) {
                    val section = schema.sections[selectedSection]
                    if (selectedSection == 0) {
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
                            schemaFields = allSchemaFields,
                            expanded = expandedSections.contains(section.id),
                            onToggle = { viewModel.toggleSection(section.id) },
                        )
                    }
                } else if (configType == FrpType.FRPC) {
                    when (selectedSection - schema.sections.size) {
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
    hasParseError: Boolean,
    onTextChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            )
        }
        if (hasParseError) {
            Text(
                text = stringResource(R.string.config_form_invalid_toml),
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
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
    val normalizedName = text.trim().removeSuffix(".toml")
    val isValidName = normalizedName.isNotEmpty() &&
        !normalizedName.contains('/') &&
        !normalizedName.contains('\\')
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        text = stringResource(R.string.dismiss),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = stringResource(R.string.confirm),
                        onClick = { onConfirm("$normalizedName.toml") },
                        modifier = Modifier.weight(1f),
                        enabled = isValidName,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        },
    )
}
