package io.github.acedroidx.frp

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.acedroidx.frp.ui.theme.AppThemeMode
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    isStartup: Boolean,
    logWrapEnabled: Boolean,
    logMaxLines: Int,
    keepAliveEnabled: Boolean,
    appLanguage: String,
    themeMode: AppThemeMode,
    useMonet: Boolean,
    allowTasker: Boolean,
    excludeFromRecents: Boolean,
    batteryOptimizationWhitelisted: Boolean,
    quickTileConfig: FrpConfig?,
    configs: List<FrpConfig>,
    exportStatusMessage: String?,
    showExportDialog: Boolean,
    showLogMaxLinesDialog: Boolean,
    onLanguageChange: (String) -> Unit,
    onThemeChange: (AppThemeMode) -> Unit,
    onUseMonetChange: (Boolean) -> Unit,
    onStartupChange: (Boolean) -> Unit,
    onKeepAliveChange: (Boolean) -> Unit,
    onBatteryOptimizationClick: () -> Unit,
    onLogWrapChange: (Boolean) -> Unit,
    onLogMaxLinesClick: () -> Unit,
    onQuickTileConfigChange: (FrpConfig?) -> Unit,
    onTaskerChange: (Boolean) -> Unit,
    onExcludeFromRecentsChange: (Boolean) -> Unit,
    onExportClick: () -> Unit,
    onAboutClick: () -> Unit,
    onExportDialogDismiss: () -> Unit,
    onExportConfirm: (String) -> Unit,
    onLogMaxLinesDialogDismiss: () -> Unit,
    onLogMaxLinesConfirm: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val languageOptions = listOf("system", "zh", "en")
    val languageLabelMap = mapOf(
        "system" to stringResource(R.string.language_system),
        "zh" to stringResource(R.string.language_chinese),
        "en" to stringResource(R.string.language_english),
    )
    val themeOptions = AppThemeMode.entries

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SettingsSection(title = stringResource(R.string.settings_section_display)) {
            OverlaySpinnerPreference(
                title = stringResource(R.string.language_title),
                items = languageOptions.map { SpinnerEntry(title = languageLabelMap[it]) },
                selectedIndex = languageOptions.indexOf(appLanguage).coerceAtLeast(0),
                onSelectedIndexChange = { index -> onLanguageChange(languageOptions[index]) },
            )
            OverlaySpinnerPreference(
                title = stringResource(R.string.theme_mode),
                items = themeOptions.map { SpinnerEntry(title = stringResource(it.labelRes)) },
                selectedIndex = themeOptions.indexOf(themeMode).coerceAtLeast(0),
                onSelectedIndexChange = { index -> onThemeChange(themeOptions[index]) },
            )
            SettingItemWithSwitch(
                title = stringResource(R.string.theme_use_monet),
                checked = useMonet,
                onCheckedChange = onUseMonetChange,
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_runtime)) {
            SettingItemWithSwitch(
                title = stringResource(R.string.auto_start_switch),
                checked = isStartup,
                onCheckedChange = onStartupChange,
            )
            SettingItemWithSwitch(
                title = stringResource(R.string.keep_alive_switch),
                checked = keepAliveEnabled,
                onCheckedChange = onKeepAliveChange,
            )
            ArrowPreference(
                title = stringResource(R.string.battery_optimization_guide_title),
                summary = when {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.M ->
                        stringResource(R.string.battery_optimization_not_applicable)
                    batteryOptimizationWhitelisted ->
                        stringResource(R.string.battery_optimization_whitelisted)
                    else ->
                        stringResource(R.string.battery_optimization_not_whitelisted)
                },
                onClick = onBatteryOptimizationClick,
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_logs)) {
            SettingItemWithSwitch(
                title = stringResource(R.string.log_wrap_switch),
                checked = logWrapEnabled,
                onCheckedChange = onLogWrapChange,
            )
            ArrowPreference(
                title = stringResource(R.string.log_max_lines_title),
                summary = stringResource(R.string.log_max_lines_value, logMaxLines),
                onClick = onLogMaxLinesClick,
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_automation)) {
            SettingItemWithConfigSelector(
                title = stringResource(R.string.quick_tile_config),
                currentConfig = quickTileConfig,
                configs = configs,
                onConfigChange = onQuickTileConfigChange,
            )
            SettingItemWithSwitch(
                title = stringResource(R.string.allow_tasker),
                checked = allowTasker,
                onCheckedChange = onTaskerChange,
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_behavior)) {
            SettingItemWithSwitch(
                title = stringResource(R.string.exclude_from_recents),
                checked = excludeFromRecents,
                onCheckedChange = onExcludeFromRecentsChange,
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_data)) {
            SettingItemExportConfig(
                title = stringResource(R.string.export_config),
                statusMessage = exportStatusMessage,
                onClick = onExportClick,
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_about)) {
            ArrowPreference(
                title = stringResource(R.string.aboutButton),
                onClick = onAboutClick,
            )
        }
    }

    if (showExportDialog) {
        ExportConfigDialog(
            onDismiss = onExportDialogDismiss,
            onConfirm = onExportConfirm,
        )
    }
    if (showLogMaxLinesDialog) {
        LogMaxLinesDialog(
            initialMaxLines = logMaxLines,
            onConfirm = onLogMaxLinesConfirm,
            onDismiss = onLogMaxLinesDialogDismiss,
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(text = title)
        Card(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun SettingItemWithSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SwitchPreference(
        title = title,
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
private fun SettingItemWithConfigSelector(
    title: String,
    currentConfig: FrpConfig?,
    configs: List<FrpConfig>,
    onConfigChange: (FrpConfig?) -> Unit,
) {
    val displayValue = currentConfig?.let {
        "${it.type.typeName}: ${it.fileName.removeSuffix(".toml")}"
    } ?: stringResource(R.string.quick_tile_not_selected)
    val options = listOf(stringResource(R.string.quick_tile_not_selected)) + configs.map {
        "${it.type.typeName}: ${it.fileName.removeSuffix(".toml")}"
    }
    val selectedIndex = currentConfig?.let { config ->
        configs.indexOf(config).takeIf { it >= 0 }?.plus(1)
    } ?: 0

    OverlayDropdownPreference(
        title = title,
        summary = displayValue,
        items = options,
        selectedIndex = selectedIndex.coerceIn(options.indices),
        onSelectedIndexChange = { index -> onConfigChange(configs.getOrNull(index - 1)) },
    )
}

@Composable
private fun SettingItemExportConfig(
    title: String,
    statusMessage: String?,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            onClick = onClick,
            endActions = {
                Icon(
                    imageVector = MiuixIcons.UploadCloud,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {
            Text(text = title, style = MiuixTheme.textStyles.body1)
        }
        if (statusMessage != null) {
            Text(
                text = statusMessage,
                style = MiuixTheme.textStyles.paragraph,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ExportConfigDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var fileName by remember { mutableStateOf("FRP_config") }

    OverlayDialog(
        show = true,
        title = stringResource(R.string.export_config_dialog_title),
        onDismissRequest = onDismiss,
        content = {
            Column {
                TextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = stringResource(R.string.export_config_file_name),
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
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
                        onClick = {
                            onConfirm(fileName)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        },
    )
}

@Composable
private fun LogMaxLinesDialog(
    initialMaxLines: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf(initialMaxLines.toString()) }

    OverlayDialog(
        show = true,
        title = stringResource(R.string.log_max_lines_title),
        onDismissRequest = onDismiss,
        content = {
            Column {
                TextField(
                    value = input,
                    onValueChange = { value -> input = value.filter { it.isDigit() }.take(3) },
                    label = stringResource(R.string.log_max_lines_hint),
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
                        onClick = {
                            onConfirm(input.toIntOrNull() ?: 20)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        },
    )
}
