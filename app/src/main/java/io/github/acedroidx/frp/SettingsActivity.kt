package io.github.acedroidx.frp

import android.app.ActivityManager
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.acedroidx.frp.ui.theme.FrpTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val themeMode = MutableStateFlow("跟随系统")
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

    @OptIn(ExperimentalMaterial3Api::class)
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

        // 读取主题设置，默认为跟随系统
        val savedTheme = preferences.getString(PreferencesKey.THEME_MODE, "跟随系统") ?: "跟随系统"
        themeMode.value = savedTheme

        // 读取 Tasker 权限设置，默认为允许
        allowTasker.value = preferences.getBoolean(PreferencesKey.ALLOW_TASKER, true)

        // 读取"最近任务中排除"设置，默认为不排除
        excludeFromRecents.value = preferences.getBoolean(PreferencesKey.EXCLUDE_FROM_RECENTS, false)

        // 加载配置列表
        loadConfigList()

        // 读取快捷开关配置
        loadQuickTileConfig()
        refreshBatteryOptimizationStatus()

        enableEdgeToEdge()
        setContent {
            val currentTheme by themeMode.collectAsStateWithLifecycle("跟随系统")
            FrpTheme(themeMode = currentTheme) {
                Scaffold(topBar = {
                    TopAppBar(
                        title = {
                            Text(stringResource(R.string.settings_title))
                        },
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
                            .verticalScroll(rememberScrollState())
                    ) {
                        SettingsContent()
                    }
                }

                // 导出配置对话框
                if (showExportDialog.value) {
                    ExportConfigDialog(onDismiss = { showExportDialog.value = false })
                }

                if (showLogMaxLinesDialog.value) {
                    LogMaxLinesDialog(onDismiss = { showLogMaxLinesDialog.value = false })
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
        val currentTheme by themeMode.collectAsStateWithLifecycle("跟随系统")
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
        val currentLanguageLabel = languageLabelMap[currentLanguage]
            ?: languageLabelMap["system"].orEmpty()

        val themeOptions = listOf("深色", "浅色", "跟随系统", "MIUI风格")
        val themeLabelMap = mapOf(
            "深色" to stringResource(R.string.theme_dark),
            "浅色" to stringResource(R.string.theme_light),
            "跟随系统" to stringResource(R.string.theme_follow_system),
            "MIUI风格" to stringResource(R.string.theme_miuix)
        )
        val currentThemeLabel = themeLabelMap[currentTheme]
            ?: themeLabelMap["跟随系统"].orEmpty()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsSection {
                SettingItemWithDropdown(
                    title = stringResource(R.string.language_title),
                    currentValue = currentLanguageLabel,
                    options = languageOptions.map { languageLabelMap[it].orEmpty() },
                    onValueChange = { selected ->
                        val newLanguage = languageOptions.firstOrNull {
                            languageLabelMap[it] == selected
                        } ?: "system"
                        if (newLanguage != appLanguage.value) {
                            preferences.edit().putString(PreferencesKey.APP_LANGUAGE, newLanguage).apply()
                            appLanguage.value = newLanguage
                            recreate()
                        }
                    }
                )
                SectionDivider()
                SettingItemWithDropdown(
                    title = stringResource(R.string.theme_mode),
                    currentValue = currentThemeLabel,
                    options = themeOptions.map { themeLabelMap[it].orEmpty() },
                    onValueChange = { selected ->
                        val newTheme = themeOptions.firstOrNull {
                            themeLabelMap[it] == selected
                        } ?: "跟随系统"
                        preferences.edit().putString(PreferencesKey.THEME_MODE, newTheme).apply()
                        themeMode.value = newTheme
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
                SectionDivider()
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
                SectionDivider()
                SettingItemWithStatus(
                    title = stringResource(R.string.battery_optimization_guide_title),
                    status = when {
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
                SectionDivider()
                SettingItemWithStatus(
                    title = stringResource(R.string.log_max_lines_title),
                    status = stringResource(R.string.log_max_lines_value, currentLogMaxLines),
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
                SectionDivider()
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
                SettingItemExportConfig(
                    title = stringResource(R.string.export_config),
                    statusMessage = exportStatus,
                    onClick = {
                        requestExportConfig()
                    }
                )
            }

            SettingsSection {
                SettingItemClickable(
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    content = content
                )
            }
        }
    }

    @Composable
    fun SectionDivider() {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
    }

    @Composable
    fun SettingItemWithStatus(
        title: String,
        status: String,
        onClick: () -> Unit
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right_24dp),
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
    }

    @Composable
    fun SettingItemWithSwitch(
        title: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }

    @Composable
    fun SettingItemWithDropdown(
        title: String,
        currentValue: String,
        options: List<String>,
        onValueChange: (String) -> Unit
    ) {
        var expanded by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Box {
                Text(
                    text = currentValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onValueChange(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun SettingItemClickable(
        title: String,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right_24dp),
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Box {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    // 不选择选项
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.quick_tile_not_selected)) },
                        onClick = {
                            onConfigChange(null)
                            expanded = false
                        }
                    )
                    // 配置列表
                    configs.forEach { config ->
                        DropdownMenuItem(
                            text = {
                                Text("${config.type.typeName}: ${config.fileName.removeSuffix(".toml")}")
                            },
                            onClick = {
                                onConfigChange(config)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
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
                    style = MaterialTheme.typography.bodyLarge
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
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ExportConfigDialog(onDismiss: () -> Unit) {
        var fileName by remember { mutableStateOf("FRP_config") }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.export_config_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text(stringResource(R.string.export_config_file_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        launchExportDocument(fileName)
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun LogMaxLinesDialog(onDismiss: () -> Unit) {
        var input by remember { mutableStateOf(logMaxLines.value.toString()) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.log_max_lines_title)) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        input = value.filter { it.isDigit() }.take(3)
                    },
                    label = { Text(stringResource(R.string.log_max_lines_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = input.toIntOrNull()
                        val maxLines = sanitizeLogMaxLines(parsed ?: DEFAULT_LOG_MAX_LINES)
                        preferences.edit().putInt(PreferencesKey.LOG_MAX_LINES, maxLines).apply()
                        logMaxLines.value = maxLines
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dismiss))
                }
            }
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
