package io.github.acedroidx.frp

import android.app.ActivityManager
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.acedroidx.frp.ui.theme.AppThemeMode
import io.github.acedroidx.frp.ui.theme.FrpTheme
import io.github.acedroidx.frp.ui.theme.putAppThemeMode
import io.github.acedroidx.frp.ui.theme.putUseMonet
import io.github.acedroidx.frp.ui.theme.readAppThemeMode
import io.github.acedroidx.frp.ui.theme.readUseMonet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SettingsActivity : BaseActivity() {
    companion object {
        private const val DEFAULT_LOG_MAX_LINES = 20
        private const val MIN_LOG_MAX_LINES = 1
        private const val MAX_LOG_MAX_LINES = 500
    }

    private val isStartup = MutableStateFlow(false)
    private val logWrapEnabled = MutableStateFlow(true)
    private val logMaxLines = MutableStateFlow(DEFAULT_LOG_MAX_LINES)
    private val keepAliveEnabled = MutableStateFlow(false)
    private val appLanguage = MutableStateFlow("system")
    private val themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    private val useMonet = MutableStateFlow(false)
    private val allowTasker = MutableStateFlow(true)
    private val excludeFromRecents = MutableStateFlow(false)
    private val batteryOptimizationWhitelisted = MutableStateFlow(false)
    private val quickTileConfig = MutableStateFlow<FrpConfig?>(null)
    private val exportStatusMessage = MutableStateFlow<String?>(null)
    private lateinit var preferences: SharedPreferences

    // 配置列表
    private val allConfigs = MutableStateFlow<List<FrpConfig>>(emptyList())

    private val exportDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launch {
                exportConfigsToUri(uri)
            }
        }
    }

    private val showExportDialog = mutableStateOf(false)
    private val showLogMaxLinesDialog = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = getSharedPreferences("data", MODE_PRIVATE)
        isStartup.value = preferences.getBoolean(PreferencesKey.AUTO_START, false)
        logWrapEnabled.value = preferences.getBoolean(PreferencesKey.LOG_WRAP_ENABLED, true)
        logMaxLines.value = sanitizeLogMaxLines(
            preferences.getInt(PreferencesKey.LOG_MAX_LINES, DEFAULT_LOG_MAX_LINES)
        )
        keepAliveEnabled.value = preferences.getBoolean(PreferencesKey.KEEP_ALIVE_ENABLED, false)
        appLanguage.value = preferences.getString(PreferencesKey.APP_LANGUAGE, "system") ?: "system"
        themeMode.value = preferences.readAppThemeMode()
        useMonet.value = preferences.readUseMonet()

        // 读取 Tasker 权限设置，默认为允许
        allowTasker.value = preferences.getBoolean(PreferencesKey.ALLOW_TASKER, true)

        // 读取"最近任务中排除"设置，默认为不排除
        excludeFromRecents.value = preferences.getBoolean(PreferencesKey.EXCLUDE_FROM_RECENTS, false)

        // 加载配置列表
        loadConfigList()

        // 读取快捷开关配置
        loadQuickTileConfig()
        refreshBatteryOptimizationStatus()

        applyEdgeToEdge()
        setContent {
        val navEventOwner = rememberNavigationEventDispatcherOwner(enabled = true, parent = null)
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides navEventOwner) {
            val currentTheme by themeMode.collectAsStateWithLifecycle(AppThemeMode.SYSTEM)
            val currentUseMonet by useMonet.collectAsStateWithLifecycle(false)
            FrpTheme(themeMode = currentTheme, useMonet = currentUseMonet) {
                Scaffold(topBar = {
                    SmallTopAppBar(
                        title = stringResource(R.string.settings_title),
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_arrow_back_24dp),
                                    contentDescription = stringResource(R.string.back)
                                )
                            }
                        }
                    )
                }) { contentPadding ->
                    Box(
                        modifier = Modifier
                            .padding(contentPadding)
                            .consumeWindowInsets(WindowInsets.ime)
                            .verticalScroll(rememberScrollState())
                    ) {
                        SettingsContent()
                    }

                    // 导出配置对话框
                    if (showExportDialog.value) {
                        ExportConfigDialog(onDismiss = { showExportDialog.value = false })
                    }

                    if (showLogMaxLinesDialog.value) {
                        LogMaxLinesDialog(
                            initialMaxLines = logMaxLines.value,
                            onConfirm = { maxLines ->
                                preferences.edit().putInt(PreferencesKey.LOG_MAX_LINES, maxLines).apply()
                                logMaxLines.value = maxLines
                            },
                            onDismiss = { showLogMaxLinesDialog.value = false },
                        )
                    }
                }
            }
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun SettingsContent() {
        val isAutoStart by isStartup.collectAsStateWithLifecycle(false)
        val isLogWrapEnabled by logWrapEnabled.collectAsStateWithLifecycle(true)
        val currentLogMaxLines by logMaxLines.collectAsStateWithLifecycle(DEFAULT_LOG_MAX_LINES)
        val isKeepAliveEnabled by keepAliveEnabled.collectAsStateWithLifecycle(false)
        val currentLanguage by appLanguage.collectAsStateWithLifecycle("system")
        val currentTheme by themeMode.collectAsStateWithLifecycle(AppThemeMode.SYSTEM)
        val currentUseMonet by useMonet.collectAsStateWithLifecycle(false)
        val isTaskerAllowed by allowTasker.collectAsStateWithLifecycle(true)
        val isExcludeFromRecents by excludeFromRecents.collectAsStateWithLifecycle(false)
        val isBatteryOptimizationWhitelisted by batteryOptimizationWhitelisted.collectAsStateWithLifecycle(false)
        val currentQuickTileConfig by quickTileConfig.collectAsStateWithLifecycle(null)
        val configs by allConfigs.collectAsStateWithLifecycle(emptyList())

        val languageOptions = listOf("system", "zh", "en")
        val languageLabelMap = mapOf(
            "system" to stringResource(R.string.language_system),
            "zh" to stringResource(R.string.language_chinese),
            "en" to stringResource(R.string.language_english)
        )

        val themeOptions = AppThemeMode.entries

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsSection {
                OverlaySpinnerPreference(
                    title = stringResource(R.string.language_title),
                    items = languageOptions.map { SpinnerEntry(title = languageLabelMap[it]) },
                    selectedIndex = languageOptions.indexOf(currentLanguage).coerceAtLeast(0),
                    onSelectedIndexChange = { idx ->
                        val newLanguage = languageOptions[idx]
                        if (newLanguage != appLanguage.value) {
                            preferences.edit().putString(PreferencesKey.APP_LANGUAGE, newLanguage).apply()
                            appLanguage.value = newLanguage
                            recreate()
                        }
                    },
                )
                OverlaySpinnerPreference(
                    title = stringResource(R.string.theme_mode),
                    items = themeOptions.map { SpinnerEntry(title = stringResource(it.labelRes)) },
                    selectedIndex = themeOptions.indexOf(currentTheme).coerceAtLeast(0),
                    onSelectedIndexChange = { idx ->
                        val newTheme = themeOptions[idx]
                        if (newTheme != themeMode.value) {
                            preferences.edit().putAppThemeMode(newTheme).apply()
                            AppCompatDelegate.setDefaultNightMode(newTheme.nightMode)
                            themeMode.value = newTheme
                            recreate()
                        }
                    },
                )
                SettingItemWithSwitch(
                    title = stringResource(R.string.theme_use_monet),
                    checked = currentUseMonet,
                    onCheckedChange = { checked ->
                        preferences.edit().putUseMonet(checked).apply()
                        useMonet.value = checked
                        recreate()
                    }
                )
            }

            SettingsSection {
                SettingItemWithSwitch(
                    title = stringResource(R.string.auto_start_switch),
                    checked = isAutoStart,
                    onCheckedChange = { checked ->
                        preferences.edit().putBoolean(PreferencesKey.AUTO_START, checked).apply()
                        isStartup.value = checked
                    }
                )
                SettingItemWithSwitch(
                    title = stringResource(R.string.keep_alive_switch),
                    checked = isKeepAliveEnabled,
                    onCheckedChange = { checked ->
                        val editor = preferences.edit()
                        editor.putBoolean(PreferencesKey.KEEP_ALIVE_ENABLED, checked)
                        if (!checked) {
                            editor.remove(PreferencesKey.KEEP_ALIVE_FRPC_LIST)
                            editor.remove(PreferencesKey.KEEP_ALIVE_FRPS_LIST)
                        }
                        editor.apply()
                        keepAliveEnabled.value = checked
                    }
                )
                ArrowPreference(
                    title = stringResource(R.string.battery_optimization_guide_title),
                    summary = when {
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ->
                            stringResource(R.string.battery_optimization_not_applicable)
                        isBatteryOptimizationWhitelisted ->
                            stringResource(R.string.battery_optimization_whitelisted)
                        else ->
                            stringResource(R.string.battery_optimization_not_whitelisted)
                    },
                    onClick = {
                        startActivity(Intent(this@SettingsActivity, BatteryOptimizationGuideActivity::class.java))
                    }
                )
            }

            SettingsSection {
                SettingItemWithSwitch(
                    title = stringResource(R.string.log_wrap_switch),
                    checked = isLogWrapEnabled,
                    onCheckedChange = { checked ->
                        preferences.edit().putBoolean(PreferencesKey.LOG_WRAP_ENABLED, checked).apply()
                        logWrapEnabled.value = checked
                    }
                )
                ArrowPreference(
                    title = stringResource(R.string.log_max_lines_title),
                    summary = stringResource(R.string.log_max_lines_value, currentLogMaxLines),
                    onClick = {
                        showLogMaxLinesDialog.value = true
                    }
                )
            }

            SettingsSection {
                SettingItemWithConfigSelector(
                    title = stringResource(R.string.quick_tile_config),
                    currentConfig = currentQuickTileConfig,
                    configs = configs,
                    onConfigChange = { config ->
                        val editor = preferences.edit()
                        if (config != null) {
                            editor.putString(PreferencesKey.QUICK_TILE_CONFIG_TYPE, config.type.name)
                            editor.putString(PreferencesKey.QUICK_TILE_CONFIG_NAME, config.fileName)
                        } else {
                            editor.remove(PreferencesKey.QUICK_TILE_CONFIG_TYPE)
                            editor.remove(PreferencesKey.QUICK_TILE_CONFIG_NAME)
                        }
                        editor.apply()
                        quickTileConfig.value = config
                    }
                )
                SettingItemWithSwitch(
                    title = stringResource(R.string.allow_tasker),
                    checked = isTaskerAllowed,
                    onCheckedChange = { checked ->
                        preferences.edit().putBoolean(PreferencesKey.ALLOW_TASKER, checked).apply()
                        allowTasker.value = checked
                    }
                )
            }

            SettingsSection {
                SettingItemWithSwitch(
                    title = stringResource(R.string.exclude_from_recents),
                    checked = isExcludeFromRecents,
                    onCheckedChange = { checked ->
                        preferences.edit().putBoolean(PreferencesKey.EXCLUDE_FROM_RECENTS, checked).apply()
                        excludeFromRecents.value = checked

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            try {
                                val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                                val appTasks = am.appTasks
                                if (appTasks.isNotEmpty()) {
                                    for (task in appTasks) {
                                        task.setExcludeFromRecents(checked)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("SettingsActivity", "Failed to set excludeFromRecents: ${e.message}")
                            }
                        }
                    }
                )
            }

            SettingsSection {
                val exportStatus by exportStatusMessage.collectAsStateWithLifecycle(null)
                ArrowPreference(
                    title = stringResource(R.string.export_config),
                    summary = exportStatus,
                    onClick = { requestExportConfig() }
                )
            }

            SettingsSection {
                ArrowPreference(
                    title = stringResource(R.string.aboutButton),
                    onClick = {
                        startActivity(Intent(this@SettingsActivity, AboutActivity::class.java))
                    }
                )
            }
        }
    }

    @Composable
    fun SettingsSection(content: @Composable ColumnScope.() -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            content()
        }
    }

    @Composable
    fun SettingItemWithSwitch(
        title: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {
        BasicComponent(
            title = title,
            onClick = { onCheckedChange(!checked) },
            endActions = {
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            },
        )
    }

    @Composable
    fun SettingItemWithConfigSelector(
        title: String,
        currentConfig: FrpConfig?,
        configs: List<FrpConfig>,
        onConfigChange: (FrpConfig?) -> Unit
    ) {
        var expanded by remember { mutableStateOf(false) }

        val displayValue = currentConfig?.let {
            "${it.type.typeName}: ${it.fileName.removeSuffix(".toml")}"
        } ?: stringResource(R.string.quick_tile_not_selected)

        val totalItems = 1 + configs.size

        BasicComponent(
            title = title,
            summary = displayValue,
            onClick = { expanded = true },
            endActions = {
                OverlayListPopup(
                    show = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    ListPopupColumn {
                        DropdownImpl(
                            text = stringResource(R.string.quick_tile_not_selected),
                            optionSize = totalItems,
                            isSelected = currentConfig == null,
                            index = 0,
                            onSelectedIndexChange = {
                                onConfigChange(null)
                                expanded = false
                            }
                        )
                        configs.forEachIndexed { index, config ->
                            DropdownImpl(
                                text = "${config.type.typeName}: ${config.fileName.removeSuffix(".toml")}",
                                optionSize = totalItems,
                                isSelected = currentConfig == config,
                                index = index + 1,
                                onSelectedIndexChange = {
                                    onConfigChange(config)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            },
        )
    }

    private fun loadConfigList() {
        val frpcConfigs = (FrpType.FRPC.getDir(this).list()?.toList() ?: emptyList()).map {
            FrpConfig(FrpType.FRPC, it)
        }
        val frpsConfigs = (FrpType.FRPS.getDir(this).list()?.toList() ?: emptyList()).map {
            FrpConfig(FrpType.FRPS, it)
        }
        allConfigs.value = frpcConfigs + frpsConfigs
    }

    private fun loadQuickTileConfig() {
        val configType = preferences.getString(PreferencesKey.QUICK_TILE_CONFIG_TYPE, null)
        val configName = preferences.getString(PreferencesKey.QUICK_TILE_CONFIG_NAME, null)

        if (configType != null && configName != null) {
            try {
                val type = FrpType.valueOf(configType)
                val config = FrpConfig(type, configName)
                // 检查配置文件是否存在
                if (config.getFile(this).exists()) {
                    quickTileConfig.value = config
                } else {
                    // 配置文件不存在，清除设置
                    preferences.edit().apply {
                        remove(PreferencesKey.QUICK_TILE_CONFIG_TYPE)
                        remove(PreferencesKey.QUICK_TILE_CONFIG_NAME)
                        apply()
                    }
                    quickTileConfig.value = null
                }
            } catch (_: IllegalArgumentException) {
                quickTileConfig.value = null
            }
        }
    }

    override fun onResume() {
        super.onResume()
        logMaxLines.value = sanitizeLogMaxLines(
            preferences.getInt(PreferencesKey.LOG_MAX_LINES, DEFAULT_LOG_MAX_LINES)
        )
        appLanguage.value = preferences.getString(PreferencesKey.APP_LANGUAGE, "system") ?: "system"
        refreshBatteryOptimizationStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清除导出状态消息
        exportStatusMessage.value = null
    }

    @Composable
    fun SettingItemExportConfig(
        title: String,
        statusMessage: String?,
        onClick: () -> Unit
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick() }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body1
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_export),
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (statusMessage != null) {
                Text(
                    text = statusMessage,
                    style = MiuixTheme.textStyles.paragraph,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }

    @Composable
    fun ExportConfigDialog(onDismiss: () -> Unit) {
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
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
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
                                launchExportDocument(fileName)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
        )
    }

    @Composable
    fun LogMaxLinesDialog(initialMaxLines: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
        var input by remember { mutableStateOf(initialMaxLines.toString()) }

        OverlayDialog(
            show = true,
            title = stringResource(R.string.log_max_lines_title),
            onDismissRequest = onDismiss,
            content = {
                Column {
                    TextField(
                        value = input,
                        onValueChange = { value ->
                            input = value.filter { it.isDigit() }.take(3)
                        },
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
                                val parsed = input.toIntOrNull()
                                onConfirm(sanitizeLogMaxLines(parsed ?: DEFAULT_LOG_MAX_LINES))
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

    private fun requestExportConfig() {
        showExportDialog.value = true
    }

    private fun launchExportDocument(fileName: String) {
        val baseName = fileName.trim().ifEmpty { "FRP_config" }
        val exportName = if (baseName.endsWith(".zip", ignoreCase = true)) {
            baseName
        } else {
            "$baseName.zip"
        }
        exportDocumentLauncher.launch(exportName)
    }

    private suspend fun exportConfigsToUri(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val outputStream = contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Failed to open target uri")

            outputStream.use { stream ->
                ZipOutputStream(stream).use { zipOut ->
                    // 导出 FRPC 配置
                    val frpcDir = FrpType.FRPC.getDir(this@SettingsActivity)
                    if (frpcDir.exists()) {
                        frpcDir.listFiles()?.forEach { file ->
                            if (file.isFile && file.name.endsWith(".toml")) {
                                val entry = ZipEntry("FRPC/${file.name}")
                                zipOut.putNextEntry(entry)
                                FileInputStream(file).use { input ->
                                    input.copyTo(zipOut)
                                }
                                zipOut.closeEntry()
                            }
                        }
                    }

                    // 导出 FRPS 配置
                    val frpsDir = FrpType.FRPS.getDir(this@SettingsActivity)
                    if (frpsDir.exists()) {
                        frpsDir.listFiles()?.forEach { file ->
                            if (file.isFile && file.name.endsWith(".toml")) {
                                val entry = ZipEntry("FRPS/${file.name}")
                                zipOut.putNextEntry(entry)
                                FileInputStream(file).use { input ->
                                    input.copyTo(zipOut)
                                }
                                zipOut.closeEntry()
                            }
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                exportStatusMessage.value = getString(R.string.export_config_success, uri.toString())
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                exportStatusMessage.value = getString(
                    R.string.export_config_failed,
                    e.message ?: "Unknown error"
                )
            }
        }
    }

    private fun refreshBatteryOptimizationStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            batteryOptimizationWhitelisted.value = true
            return
        }

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        batteryOptimizationWhitelisted.value =
            powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun sanitizeLogMaxLines(value: Int): Int {
        return value.coerceIn(MIN_LOG_MAX_LINES, MAX_LOG_MAX_LINES)
    }
}
