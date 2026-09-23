package io.github.acedroidx.frp

import android.Manifest
import android.app.NotificationChannel
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.IBinder
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.acedroidx.frp.config.TomlParserUtil
import io.github.acedroidx.frp.ui.theme.AppThemeMode
import io.github.acedroidx.frp.ui.theme.putAppThemeMode
import io.github.acedroidx.frp.ui.theme.putUseMonet
import io.github.acedroidx.frp.ui.theme.readAppThemeMode
import io.github.acedroidx.frp.ui.theme.readUseMonet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic


class MainActivity : BaseActivity() {
    companion object {
        private const val DEFAULT_LOG_MAX_LINES = 20
        private const val MIN_LOG_MAX_LINES = 1
        private const val MAX_LOG_MAX_LINES = 500
        const val EXTRA_SELECTED_DESTINATION = "selected_destination"
        const val DESTINATION_HOME = 0
        const val DESTINATION_FRPC = 1
        const val DESTINATION_FRPS = 2
        const val DESTINATION_SETTINGS = 3
    }

    private val isStartup = MutableStateFlow(false)
    private val frpcConfigList = MutableStateFlow<List<FrpConfig>>(emptyList())
    private val frpsConfigList = MutableStateFlow<List<FrpConfig>>(emptyList())
    private val runningConfigList = MutableStateFlow<List<FrpConfig>>(emptyList())
    private val frpVersion = MutableStateFlow("Loading...")
    private val themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    private val useMonet = MutableStateFlow(false)
    private val permissionGranted = MutableStateFlow(true)
    private val logWrapEnabled = MutableStateFlow(true)
    private val logMaxLines = MutableStateFlow(DEFAULT_LOG_MAX_LINES)
    private val keepAliveEnabled = MutableStateFlow(false)
    private val appLanguage = MutableStateFlow("system")
    private val allowTasker = MutableStateFlow(true)
    private val excludeFromRecents = MutableStateFlow(false)
    private val batteryOptimizationWhitelisted = MutableStateFlow(false)
    private val quickTileConfig = MutableStateFlow<FrpConfig?>(null)
    private val exportStatusMessage = MutableStateFlow<String?>(null)
    private val allConfigs = MutableStateFlow<List<FrpConfig>>(emptyList())

    private lateinit var preferences: SharedPreferences

    private lateinit var mService: ShellService
    private var mBound: Boolean = false
    private val serviceConnected = mutableStateOf(false)
    private var processThreadsCollectJob: Job? = null

    private val selectedDestinationState = mutableIntStateOf(DESTINATION_HOME)
    private val showImportTypeDialog = mutableStateOf(false)
    private var pendingImportFile: Uri? = null
    private var appliedLanguagePreference: String = "system"
    private val configRefreshCounter = mutableStateOf(0)
    private val showExportDialog = mutableStateOf(false)
    private val showLogMaxLinesDialog = mutableStateOf(false)

    private val exportDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let {
            lifecycleScope.launch { exportConfigsToUri(it) }
        }
    }

    // 权限请求启动器
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        permissionGranted.value = isGranted
        if (!isGranted) {
            Log.w("adx", "Notification permission denied")
        } else {
            Log.d("adx", "Notification permission granted")
        }
    }

    // 文件选择器 - ZIP 文件
    private val zipFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            lifecycleScope.launch {
                importZipFile(it)
            }
        }
    }

    // 文件选择器 - TOML 文件
    private val tomlFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            pendingImportFile = it
            showImportTypeDialog.value = true
        }
    }

    /** Defines callbacks for service binding, passed to bindService()  */
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as ShellService.LocalBinder
            mService = binder.getService()
            mBound = true
            serviceConnected.value = true

            // frp 版本在构建时由 Gradle 同步到 BuildConfig
            frpVersion.value = BuildConfig.FrpVersion
            preferences.edit {
                putString(PreferencesKey.FRP_VERSION, BuildConfig.FrpVersion)
            }

            processThreadsCollectJob?.cancel()
            processThreadsCollectJob = lifecycleScope.launch {
                mService.processThreads.collect { processThreads ->
                    runningConfigList.value = processThreads.keys.toList()
                }
            }

            // 打开应用自动启动标记的配置
            autoStartOnAppLaunch()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            processThreadsCollectJob?.cancel()
            processThreadsCollectJob = null
            mBound = false
            serviceConnected.value = false
        }
    }

    private val configActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
            updateConfigList()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查是否需要退出应用
        if (intent.getBooleanExtra("EXIT_APP", false)) {
            finishAffinity() // 关闭所有 Activity
            return
        }

        preferences = getSharedPreferences("data", MODE_PRIVATE)
        selectedDestinationState.intValue = intent.getIntExtra(EXTRA_SELECTED_DESTINATION, DESTINATION_HOME)

        // 应用"最近任务中排除"设置
        val excludeFromRecentsSetting = preferences.getBoolean(PreferencesKey.EXCLUDE_FROM_RECENTS, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                val appTasks = am.appTasks
                if (appTasks.isNotEmpty()) {
                    for (task in appTasks) {
                        task.setExcludeFromRecents(excludeFromRecentsSetting)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to set excludeFromRecents: ${e.message}")
            }
        }

        isStartup.value = preferences.getBoolean(PreferencesKey.AUTO_START, false)
        logWrapEnabled.value = preferences.getBoolean(PreferencesKey.LOG_WRAP_ENABLED, true)
        logMaxLines.value = sanitizeLogMaxLines(
            preferences.getInt(PreferencesKey.LOG_MAX_LINES, DEFAULT_LOG_MAX_LINES)
        )
        keepAliveEnabled.value = preferences.getBoolean(PreferencesKey.KEEP_ALIVE_ENABLED, false)
        appLanguage.value = preferences.getString(PreferencesKey.APP_LANGUAGE, "system") ?: "system"
        allowTasker.value = preferences.getBoolean(PreferencesKey.ALLOW_TASKER, true)
        this.excludeFromRecents.value = preferences.getBoolean(PreferencesKey.EXCLUDE_FROM_RECENTS, false)
        frpVersion.value = preferences.getString(PreferencesKey.FRP_VERSION, "Loading...") ?: "Loading..."
        themeMode.value = preferences.readAppThemeMode()
        useMonet.value = preferences.readUseMonet()
        appliedLanguagePreference = appLanguage.value

        checkConfig()
        updateConfigList()
        loadSettingsConfigList()
        loadQuickTileConfig()
        refreshBatteryOptimizationStatus()
        createBGNotificationChannel()
        checkAndRequestPermissions()

        applyEdgeToEdge()
        setContent {
            FrpThemedContent(themeMode = themeMode, useMonet = useMonet) {
                val openDialog = remember { mutableStateOf(false) }
                val snackbarHostState = remember { SnackbarHostState() }
                val permissionGranted by permissionGranted.collectAsStateWithLifecycle(true)
                val selectedDestination = selectedDestinationState.intValue
                val currentIsStartup by isStartup.collectAsStateWithLifecycle(false)
                val currentLogWrapEnabled by logWrapEnabled.collectAsStateWithLifecycle(true)
                val currentLogMaxLines by logMaxLines.collectAsStateWithLifecycle(DEFAULT_LOG_MAX_LINES)
                val currentKeepAliveEnabled by keepAliveEnabled.collectAsStateWithLifecycle(false)
                val currentAppLanguage by appLanguage.collectAsStateWithLifecycle("system")
                val currentThemeMode by themeMode.collectAsStateWithLifecycle(AppThemeMode.SYSTEM)
                val currentUseMonet by useMonet.collectAsStateWithLifecycle(false)
                val currentAllowTasker by allowTasker.collectAsStateWithLifecycle(true)
                val currentExcludeFromRecents by excludeFromRecents.collectAsStateWithLifecycle(false)
                val currentBatteryOptimization by batteryOptimizationWhitelisted.collectAsStateWithLifecycle(false)
                val currentQuickTileConfig by quickTileConfig.collectAsStateWithLifecycle(null)
                val currentConfigs by allConfigs.collectAsStateWithLifecycle(emptyList())
                val currentExportStatus by exportStatusMessage.collectAsStateWithLifecycle(null)
                val scrollBehavior = MiuixScrollBehavior()

                BackHandler(enabled = selectedDestination != DESTINATION_HOME) {
                    selectedDestinationState.intValue = DESTINATION_HOME
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = when (selectedDestination) {
                                DESTINATION_FRPC -> stringResource(R.string.nav_frpc)
                                DESTINATION_FRPS -> stringResource(R.string.nav_frps)
                                DESTINATION_SETTINGS -> stringResource(R.string.settings_title)
                                else -> stringResource(R.string.frp_for_android)
                            },
                            scrollBehavior = scrollBehavior,
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            listOf(
                                NavigationItem(stringResource(R.string.nav_home), MiuixIcons.Home),
                                NavigationItem(stringResource(R.string.nav_frpc), MiuixIcons.Link),
                                NavigationItem(stringResource(R.string.nav_frps), MiuixIcons.CloudFill),
                                NavigationItem(stringResource(R.string.settings_title), MiuixIcons.Settings),
                            ).forEachIndexed { index, item ->
                                NavigationBarItem(
                                    selected = selectedDestination == index,
                                    onClick = { selectedDestinationState.intValue = index },
                                    icon = item.icon,
                                    label = item.label,
                                )
                            }
                        }
                    },
                    floatingActionButton = {
                        if (selectedDestination != DESTINATION_SETTINGS) {
                            FloatingActionButton(
                                onClick = {
                                    when (selectedDestination) {
                                        DESTINATION_FRPC -> startConfigActivity(FrpType.FRPC)
                                        DESTINATION_FRPS -> startConfigActivity(FrpType.FRPS)
                                        else -> openDialog.value = true
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Add,
                                    contentDescription = stringResource(R.string.addConfigButton)
                                )
                            }
                        }
                    },
                    snackbarHost = {
                        SnackbarHost(state = snackbarHostState)
                    }
                ) { contentPadding ->
                    AnimatedContent(
                        targetState = selectedDestination,
                        modifier = Modifier.padding(contentPadding).fillMaxSize(),
                        transitionSpec = {
                            val forward = targetState > initialState
                            val enterOffset: (Int) -> Int = { width -> if (forward) width else -width }
                            val exitOffset: (Int) -> Int = { width -> if (forward) -width else width }
                            (slideInHorizontally(
                                animationSpec = tween(280),
                                initialOffsetX = enterOffset,
                            ) + fadeIn(animationSpec = tween(180))) togetherWith
                                (slideOutHorizontally(
                                    animationSpec = tween(280),
                                    targetOffsetX = exitOffset,
                                ) + fadeOut(animationSpec = tween(180)))
                        },
                        label = "main_destination_transition",
                    ) { destination ->
                        val pageModifier = Modifier.fillMaxSize()
                        when (destination) {
                            DESTINATION_FRPC -> ConfigListContent(
                                configType = FrpType.FRPC,
                                modifier = pageModifier,
                                scrollConnection = scrollBehavior.nestedScrollConnection,
                            )
                            DESTINATION_FRPS -> ConfigListContent(
                                configType = FrpType.FRPS,
                                modifier = pageModifier,
                                scrollConnection = scrollBehavior.nestedScrollConnection,
                            )
                            DESTINATION_SETTINGS -> SettingsScreen(
                                isStartup = currentIsStartup,
                                logWrapEnabled = currentLogWrapEnabled,
                                logMaxLines = currentLogMaxLines,
                                keepAliveEnabled = currentKeepAliveEnabled,
                                appLanguage = currentAppLanguage,
                                themeMode = currentThemeMode,
                                useMonet = currentUseMonet,
                                allowTasker = currentAllowTasker,
                                excludeFromRecents = currentExcludeFromRecents,
                                batteryOptimizationWhitelisted = currentBatteryOptimization,
                                quickTileConfig = currentQuickTileConfig,
                                configs = currentConfigs,
                                exportStatusMessage = currentExportStatus,
                                showExportDialog = showExportDialog.value,
                                showLogMaxLinesDialog = showLogMaxLinesDialog.value,
                                onLanguageChange = ::updateLanguage,
                                onThemeChange = ::updateTheme,
                                onUseMonetChange = ::updateUseMonet,
                                onStartupChange = ::updateStartup,
                                onKeepAliveChange = ::updateKeepAlive,
                                onBatteryOptimizationClick = {
                                    startActivity(Intent(this@MainActivity, BatteryOptimizationGuideActivity::class.java))
                                },
                                onLogWrapChange = ::updateLogWrap,
                                onLogMaxLinesClick = { showLogMaxLinesDialog.value = true },
                                onQuickTileConfigChange = ::updateQuickTileConfig,
                                onTaskerChange = ::updateTasker,
                                onExcludeFromRecentsChange = ::updateExcludeFromRecents,
                                onExportClick = { showExportDialog.value = true },
                                onAboutClick = {
                                    startActivity(Intent(this@MainActivity, AboutActivity::class.java))
                                },
                                onExportDialogDismiss = { showExportDialog.value = false },
                                onExportConfirm = ::launchExportDocument,
                                onLogMaxLinesDialogDismiss = { showLogMaxLinesDialog.value = false },
                                onLogMaxLinesConfirm = { value ->
                                    val sanitized = sanitizeLogMaxLines(value)
                                    preferences.edit().putInt(PreferencesKey.LOG_MAX_LINES, sanitized).apply()
                                    logMaxLines.value = sanitized
                                },
                                modifier = pageModifier
                                    .verticalScroll(rememberScrollState())
                                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                                    .scrollEndHaptic()
                                    .overScrollVertical(),
                            )
                            else -> MainContent(
                                modifier = pageModifier,
                                scrollConnection = scrollBehavior.nestedScrollConnection,
                            )
                        }
                    }

                    CreateConfigDialog(
                        show = openDialog.value,
                        onDismissFinished = { openDialog.value = false },
                        onClose = { openDialog.value = false },
                    )

                    if (showImportTypeDialog.value) {
                        ImportTypeDialog { showImportTypeDialog.value = false }
                    }
                }

                // 显示权限提示
                val scope = rememberCoroutineScope()
                LaunchedEffect(permissionGranted) {
                    if (!permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = getString(R.string.permission_notification_snackbar_message),
                                actionLabel = getString(R.string.open_settings),
                                withDismissAction = true
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                // 跳转到应用设置页面
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", packageName, null)
                                }
                                startActivity(intent)
                            }
                        }
                    }
                }
            }
        }

        if (!mBound) {
            val intent = Intent(this, ShellService::class.java)
            bindService(intent, connection, BIND_AUTO_CREATE)
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun MainContent(
        modifier: Modifier = Modifier,
        scrollConnection: NestedScrollConnection? = null,
    ) {
        val frpcConfigs by frpcConfigList.collectAsStateWithLifecycle(emptyList())
        val frpsConfigs by frpsConfigList.collectAsStateWithLifecycle(emptyList())
        val runningConfigs by runningConfigList.collectAsStateWithLifecycle(emptyList())
        val version by frpVersion.collectAsStateWithLifecycle("Loading...")
        val startup by isStartup.collectAsStateWithLifecycle(false)
        val keepAlive by keepAliveEnabled.collectAsStateWithLifecycle(false)
        val notificationAllowed by permissionGranted.collectAsStateWithLifecycle(true)
        val nestedScrollConnection = scrollConnection ?: MiuixScrollBehavior().nestedScrollConnection
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .nestedScroll(nestedScrollConnection)
                .scrollEndHaptic()
                .overScrollVertical(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            overscrollEffect = null,
        ) {
            item(key = "dashboard") {
                HomeDashboard(
                    totalCount = frpcConfigs.size + frpsConfigs.size,
                    runningConfigs = runningConfigs,
                    isServiceConnected = serviceConnected.value,
                    frpVersion = version,
                    isStartup = startup,
                    keepAliveEnabled = keepAlive,
                    notificationAllowed = notificationAllowed,
                )
            }
        }
    }

    @Composable
    private fun ConfigListContent(
        configType: FrpType,
        modifier: Modifier = Modifier,
        scrollConnection: NestedScrollConnection,
    ) {
        val configs by (if (configType == FrpType.FRPC) frpcConfigList else frpsConfigList)
            .collectAsStateWithLifecycle(emptyList())
        val runningConfigs by runningConfigList.collectAsStateWithLifecycle(emptyList())
        val isLogWrapEnabled by logWrapEnabled.collectAsStateWithLifecycle(true)
        var query by remember(configType) { mutableStateOf("") }
        var searchExpanded by remember(configType) { mutableStateOf(false) }
        val normalizedQuery = query.trim()
        val filteredConfigs = configs.filter { config ->
            normalizedQuery.isEmpty() || config.fileName.contains(normalizedQuery, ignoreCase = true)
        }

        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .nestedScroll(scrollConnection)
                .scrollEndHaptic()
                .overScrollVertical(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            overscrollEffect = null,
        ) {
            item(key = "search") {
                    SearchBar(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                        inputField = {
                            InputField(
                                query = query,
                                onQueryChange = { query = it },
                                onSearch = { searchExpanded = false },
                                expanded = searchExpanded,
                                onExpandedChange = { searchExpanded = it },
                                label = stringResource(R.string.search_config),
                            )
                        },
                    ) { }
            }
            if (filteredConfigs.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = if (normalizedQuery.isEmpty()) stringResource(R.string.no_config)
                        else stringResource(R.string.no_matching_config),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            items(
                items = filteredConfigs,
                key = { config -> "${config.type.typeName}:${config.fileName}" },
            ) { config ->
                FrpConfigItem(config, runningConfigs, isLogWrapEnabled)
            }
        }
    }

    @Composable
    private fun HomeDashboard(
        totalCount: Int,
        runningConfigs: List<FrpConfig>,
        isServiceConnected: Boolean,
        frpVersion: String,
        isStartup: Boolean,
        keepAliveEnabled: Boolean,
        notificationAllowed: Boolean,
    ) {
        val isActive = isServiceConnected
        val isDynamicColor = MiuixTheme.isDynamicColor
        val containerColor = when {
            !isActive && isDynamicColor -> MiuixTheme.colorScheme.errorContainer
            !isActive && isSystemInDarkTheme() -> Color(0xFF381A1A)
            !isActive -> Color(0xFFFAEEEE)
            isDynamicColor -> MiuixTheme.colorScheme.secondaryContainer
            isSystemInDarkTheme() -> Color(0xFF1A3825)
            else -> Color(0xFFDFFAE4)
        }
        val contentColor = when {
            !isActive && isDynamicColor -> MiuixTheme.colorScheme.onErrorContainer
            isDynamicColor -> MiuixTheme.colorScheme.onSecondaryContainer
            else -> MiuixTheme.colorScheme.onSurface
        }
        val statusTitle = when {
            !isServiceConnected -> stringResource(R.string.home_status_disconnected)
            runningConfigs.isNotEmpty() -> stringResource(R.string.home_status_running)
            else -> stringResource(R.string.home_status_ready)
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(color = containerColor),
                onClick = {},
                showIndication = true,
                pressFeedbackType = PressFeedbackType.Tilt,
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .offset(x = 50.dp, y = 38.dp),
                        contentAlignment = Alignment.BottomEnd,
                    ) {
                        Icon(
                            modifier = Modifier.size(170.dp),
                            imageVector = MiuixIcons.CloudFill,
                            tint = if (isActive) {
                                if (isDynamicColor) MiuixTheme.colorScheme.primary.copy(alpha = 0.8f)
                                else Color(0xFF36D167)
                            } else {
                                if (isDynamicColor) MiuixTheme.colorScheme.error.copy(alpha = 0.8f)
                                else Color(0xFFD13636)
                            },
                            contentDescription = null,
                        )
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Text(
                            text = statusTitle,
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MiuixStatCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    title = stringResource(R.string.home_running_count),
                    value = runningConfigs.size.toString(),
                )
                MiuixStatCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    title = stringResource(R.string.home_total_configs),
                    value = totalCount.toString(),
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.home_environment_title), style = MiuixTheme.textStyles.headline2)
                    HomeInfoRow(stringResource(R.string.home_frp_version), frpVersion)
                    HomeInfoRow(
                        stringResource(R.string.home_android_version),
                        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    )
                    HomeInfoRow(stringResource(R.string.home_app_version_label), BuildConfig.VERSION_NAME)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.home_stability_title), style = MiuixTheme.textStyles.headline2)
                    HomeInfoRow(
                        stringResource(R.string.auto_start_switch),
                        stringResource(if (isStartup) R.string.home_enabled else R.string.home_disabled),
                    )
                    HomeInfoRow(
                        stringResource(R.string.keep_alive_switch),
                        stringResource(if (keepAliveEnabled) R.string.home_enabled else R.string.home_disabled),
                    )
                    HomeInfoRow(
                        stringResource(R.string.permission_notification_title),
                        stringResource(if (notificationAllowed) R.string.home_enabled else R.string.home_attention),
                    )
                }
            }
        }
    }

    @Composable
    private fun MiuixStatCard(
        modifier: Modifier,
        title: String,
        value: String,
    ) {
        Card(
            modifier = modifier,
            insideMargin = PaddingValues(16.dp),
            showIndication = true,
            pressFeedbackType = PressFeedbackType.Tilt,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = value,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }

    @Composable
    private fun HomeInfoRow(label: String, value: String) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text(value, style = MiuixTheme.textStyles.body2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    @Composable
    fun FrpConfigItem(
        config: FrpConfig,
        runningConfigs: List<FrpConfig>,
        isLogWrapEnabled: Boolean,
    ) {
        val isRunning = runningConfigs.contains(config)
        val showLog = remember { mutableStateOf(false) }
        val showDeleteDialog = remember { mutableStateOf(false) }

        // 读取配置状态信息
        val refreshCount = configRefreshCounter.value
        val statusInfo = remember { mutableStateOf<ConfigStatusInfo?>(null) }
        LaunchedEffect(config, refreshCount) {
            statusInfo.value = withContext(Dispatchers.IO) {
                loadConfigStatusInfo(config)
            }
        }

        // 监听实时配置日志
        val configLogs by if (mBound) {
            mService.configLogs.collectAsStateWithLifecycle(emptyMap())
        } else {
            remember { MutableStateFlow(emptyMap<FrpConfig, String>()) }.collectAsStateWithLifecycle(emptyMap())
        }

        val configLog = configLogs[config] ?: ""

        // 初始化时加载日志
        LaunchedEffect(showLog.value, isRunning, mBound) {
            if (showLog.value && mBound) {
                mService.getConfigLog(config)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                cornerRadius = 12.dp,
                onClick = {
                    if (mBound) {
                        showLog.value = !showLog.value
                        if (showLog.value) {
                            mService.getConfigLog(config)
                        }
                    }
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = config.fileName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            statusInfo.value?.let { info ->
                                if (info.serverAddr.isNotEmpty()) {
                                    Text(
                                        "${stringResource(R.string.status_server)}: ${info.serverAddr}:${info.serverPort}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                val bootText = stringResource(if (info.isBootAutoStart) R.string.status_boot_auto_start else R.string.status_boot_manual)
                                val appText = stringResource(if (info.isAppLaunchAutoStart) R.string.status_app_launch_auto_start else R.string.status_app_launch_manual)
                                val defaultColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                val primaryColor = MiuixTheme.colorScheme.primary
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(color = if (info.isBootAutoStart) primaryColor else defaultColor)) {
                                            append(bootText)
                                        }
                                        append("  ")
                                        withStyle(SpanStyle(color = if (info.isAppLaunchAutoStart) primaryColor else defaultColor)) {
                                            append(appText)
                                        }
                                    },
                                    style = MiuixTheme.textStyles.footnote1,
                                )
                            }
                        }
                        Icon(
                            imageVector = if (showLog.value) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,
                            contentDescription = stringResource(
                                if (showLog.value) R.string.collapse else R.string.expand,
                            ),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IconButton(
                            onClick = { startConfigActivity(config) },
                            enabled = !isRunning,
                            modifier = Modifier.size(36.dp),
                            backgroundColor = MiuixTheme.colorScheme.secondaryVariant,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Edit,
                                contentDescription = stringResource(R.string.edit_config),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        IconButton(
                            onClick = { showDeleteDialog.value = true },
                            enabled = !isRunning,
                            modifier = Modifier.padding(start = 8.dp).size(36.dp),
                            backgroundColor = MiuixTheme.colorScheme.secondaryVariant,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Delete,
                                contentDescription = stringResource(R.string.delete_config),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Switch(
                            checked = isRunning,
                            onCheckedChange = {
                                if (it) {
                                    startShell(config)
                                } else {
                                    stopShell(config)
                                    showLog.value = false
                                }
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            // 可折叠的日志视图
            AnimatedVisibility(
                visible = showLog.value,
                enter = expandVertically(
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    )
                ),
                exit = shrinkVertically(
                    animationSpec = tween(
                        durationMillis = 250,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = 250,
                        easing = FastOutSlowInEasing
                    )
                )
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    cornerRadius = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(R.string.frp_log),
                                style = MiuixTheme.textStyles.title3
                            )
                            Button(
                                onClick = {
                                    if (mBound) {
                                        mService.clearConfigLog(config)
                                    }
                                }
                            ) {
                                Text(
                                    stringResource(R.string.deleteButton),
                                    style = MiuixTheme.textStyles.footnote2
                                )
                            }
                        }
                        SelectionContainer {
                            val displayLog = configLog.ifEmpty { stringResource(R.string.no_log) }
                            val ansiLog = remember(displayLog) {
                                parseAnsiToAnnotatedString(
                                    text = displayLog,
                                    defaultColor = androidx.compose.ui.graphics.Color.Unspecified
                                )
                            }
                            val horizontalScrollState = rememberScrollState()
                            Text(
                                text = ansiLog,
                                style = MiuixTheme.textStyles.paragraph.merge(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                softWrap = isLogWrapEnabled,
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .then(
                                        if (isLogWrapEnabled) {
                                            Modifier
                                        } else {
                                            Modifier.horizontalScroll(horizontalScrollState)
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        }

        // 删除确认对话框
        if (showDeleteDialog.value) {
            OverlayDialog(
                show = true,
                title = stringResource(R.string.confirm_delete_title),
                onDismissRequest = { showDeleteDialog.value = false },
                content = {
                    Text(stringResource(R.string.confirm_delete_message, config.fileName))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            text = stringResource(R.string.dismiss),
                            onClick = { showDeleteDialog.value = false },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(20.dp))
                        TextButton(
                            text = stringResource(R.string.deleteConfigButton),
                            onClick = {
                                deleteConfig(config)
                                showDeleteDialog.value = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            )
        }
    }

    private data class ConfigStatusInfo(
        val serverAddr: String,
        val serverPort: Long,
        val isBootAutoStart: Boolean,
        val isAppLaunchAutoStart: Boolean,
    )

    private fun loadConfigStatusInfo(config: FrpConfig): ConfigStatusInfo? {
        return try {
            val file = config.getFile(this)
            if (!file.exists()) return null
            val toml = file.readText()
            val map = TomlParserUtil.parseToMap(toml)
            val serverAddr = map["serverAddr"]?.toString() ?: ""
            val serverPort = (map["serverPort"] as? Number)?.toLong() ?: 7000L

            val bootAutoStartKey = config.type.getAutoStartPreferencesKey()
            val bootList = preferences.getStringSet(bootAutoStartKey, emptySet())
            val isBootAutoStart = bootList?.contains(config.fileName) == true

            val appLaunchKey = config.type.getAutoStartOnAppLaunchPreferencesKey()
            val appLaunchList = preferences.getStringSet(appLaunchKey, emptySet())
            val isAppLaunchAutoStart = appLaunchList?.contains(config.fileName) == true

            ConfigStatusInfo(serverAddr, serverPort, isBootAutoStart, isAppLaunchAutoStart)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to load config status info: ${e.message}")
            null
        }
    }

    @Composable
    @Preview(showBackground = true)
    fun CreateConfigDialog(
        show: Boolean = true,
        onDismissFinished: () -> Unit = {},
        onClose: () -> Unit = {},
    ) {
        OverlayBottomSheet(
            show = show,
            title = stringResource(R.string.create_frp_select),
            onDismissRequest = onClose,
            onDismissFinished = onDismissFinished,
            content = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(
                        text = "frpc",
                        onClick = { startConfigActivity(FrpType.FRPC); onClose() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        text = "frps",
                        onClick = { startConfigActivity(FrpType.FRPS); onClose() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.import_config),
                        style = MiuixTheme.textStyles.title3,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    TextButton(
                        text = stringResource(R.string.import_zip),
                        onClick = {
                            zipFileLauncher.launch("application/zip")
                            onClose()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        text = stringResource(R.string.import_toml),
                        onClick = {
                            tomlFileLauncher.launch("*/*")
                            onClose()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }

    @Composable
    fun ImportTypeDialog(onDismiss: () -> Unit) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.import_select_type),
            onDismissRequest = onDismiss,
            content = {
                Text(stringResource(R.string.import_select_type_desc))
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        text = "FRPC",
                        onClick = {
                            pendingImportFile?.let { uri ->
                                lifecycleScope.launch {
                                    importTomlFile(uri, FrpType.FRPC)
                                }
                            }
                            onDismiss()
                        }
                    )
                    TextButton(
                        text = "FRPS",
                        onClick = {
                            pendingImportFile?.let { uri ->
                                lifecycleScope.launch {
                                    importTomlFile(uri, FrpType.FRPS)
                                }
                            }
                            onDismiss()
                        }
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        text = stringResource(R.string.dismiss),
                        onClick = onDismiss
                    )
                }
            }
        )
    }

    private fun updateLanguage(language: String) {
        if (language == appLanguage.value) return
        preferences.edit().putString(PreferencesKey.APP_LANGUAGE, language).apply()
        appLanguage.value = language
        recreate()
    }

    private fun updateTheme(mode: AppThemeMode) {
        if (mode == themeMode.value) return
        preferences.edit().putAppThemeMode(mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
        themeMode.value = mode
        recreate()
    }

    private fun updateUseMonet(enabled: Boolean) {
        preferences.edit().putUseMonet(enabled).apply()
        useMonet.value = enabled
        recreate()
    }

    private fun updateStartup(enabled: Boolean) {
        preferences.edit().putBoolean(PreferencesKey.AUTO_START, enabled).apply()
        isStartup.value = enabled
    }

    private fun updateKeepAlive(enabled: Boolean) {
        preferences.edit {
            putBoolean(PreferencesKey.KEEP_ALIVE_ENABLED, enabled)
            if (!enabled) {
                remove(PreferencesKey.KEEP_ALIVE_FRPC_LIST)
                remove(PreferencesKey.KEEP_ALIVE_FRPS_LIST)
            }
        }
        keepAliveEnabled.value = enabled
    }

    private fun updateLogWrap(enabled: Boolean) {
        preferences.edit().putBoolean(PreferencesKey.LOG_WRAP_ENABLED, enabled).apply()
        logWrapEnabled.value = enabled
    }

    private fun updateTasker(enabled: Boolean) {
        preferences.edit().putBoolean(PreferencesKey.ALLOW_TASKER, enabled).apply()
        allowTasker.value = enabled
    }

    private fun updateExcludeFromRecents(enabled: Boolean) {
        preferences.edit().putBoolean(PreferencesKey.EXCLUDE_FROM_RECENTS, enabled).apply()
        excludeFromRecents.value = enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                activityManager.appTasks.forEach { task -> task.setExcludeFromRecents(enabled) }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to set excludeFromRecents: ${e.message}")
            }
        }
    }

    private fun updateQuickTileConfig(config: FrpConfig?) {
        preferences.edit {
            if (config == null) {
                remove(PreferencesKey.QUICK_TILE_CONFIG_TYPE)
                remove(PreferencesKey.QUICK_TILE_CONFIG_NAME)
            } else {
                putString(PreferencesKey.QUICK_TILE_CONFIG_TYPE, config.type.name)
                putString(PreferencesKey.QUICK_TILE_CONFIG_NAME, config.fileName)
            }
        }
        quickTileConfig.value = config
    }

    private fun loadSettingsConfigList() {
        allConfigs.value = frpcConfigList.value + frpsConfigList.value
    }

    private fun loadQuickTileConfig() {
        val configType = preferences.getString(PreferencesKey.QUICK_TILE_CONFIG_TYPE, null)
        val configName = preferences.getString(PreferencesKey.QUICK_TILE_CONFIG_NAME, null)
        quickTileConfig.value = if (configType != null && configName != null) {
            runCatching {
                FrpConfig(FrpType.valueOf(configType), configName)
            }.getOrNull()?.takeIf { it.getFile(this).exists() }
        } else {
            null
        }
    }

    private fun refreshBatteryOptimizationStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            batteryOptimizationWhitelisted.value = true
            return
        }
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        batteryOptimizationWhitelisted.value = powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun sanitizeLogMaxLines(value: Int): Int {
        return value.coerceIn(MIN_LOG_MAX_LINES, MAX_LOG_MAX_LINES)
    }

    private fun launchExportDocument(fileName: String) {
        val baseName = fileName.trim().ifEmpty { "FRP_config" }
        val exportName = if (baseName.endsWith(".zip", ignoreCase = true)) baseName else "$baseName.zip"
        exportDocumentLauncher.launch(exportName)
    }

    private suspend fun exportConfigsToUri(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val outputStream = contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Failed to open target uri")
            outputStream.use { stream ->
                ZipOutputStream(stream).use { zipOut ->
                    listOf(FrpType.FRPC, FrpType.FRPS).forEach { type ->
                        type.getDir(this@MainActivity).listFiles()?.forEach { file ->
                            if (file.isFile && file.name.endsWith(".toml")) {
                                zipOut.putNextEntry(ZipEntry("${type.typeName.uppercase(Locale.getDefault())}/${file.name}"))
                                FileInputStream(file).use { input -> input.copyTo(zipOut) }
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
                    e.message ?: "Unknown error",
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        selectedDestinationState.intValue = intent.getIntExtra(EXTRA_SELECTED_DESTINATION, DESTINATION_HOME)
    }

    override fun onResume() {
        super.onResume()
        val currentLanguagePreference =
            preferences.getString(PreferencesKey.APP_LANGUAGE, "system") ?: "system"
        val currentThemePreference = preferences.readAppThemeMode()
        val currentUseMonet = preferences.readUseMonet()
        if (currentLanguagePreference != appliedLanguagePreference ||
            currentThemePreference != themeMode.value ||
            currentUseMonet != useMonet.value
        ) {
            appliedLanguagePreference = currentLanguagePreference
            themeMode.value = currentThemePreference
            useMonet.value = currentUseMonet
            recreate()
            return
        }
        logWrapEnabled.value = preferences.getBoolean(PreferencesKey.LOG_WRAP_ENABLED, true)
        logMaxLines.value = sanitizeLogMaxLines(
            preferences.getInt(PreferencesKey.LOG_MAX_LINES, DEFAULT_LOG_MAX_LINES)
        )
        appLanguage.value = preferences.getString(PreferencesKey.APP_LANGUAGE, "system") ?: "system"
        loadSettingsConfigList()
        loadQuickTileConfig()
        refreshBatteryOptimizationStatus()

        // 重新应用"最近任务中排除"设置
        val excludeFromRecents = preferences.getBoolean(PreferencesKey.EXCLUDE_FROM_RECENTS, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                val appTasks = am.appTasks
                if (appTasks.isNotEmpty()) {
                    for (task in appTasks) {
                        task.setExcludeFromRecents(excludeFromRecents)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to set excludeFromRecents in onResume: ${e.message}")
            }
        }

        // 重新检查权限状态（用户可能从设置页面返回）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            permissionGranted.value = hasNotificationPermission
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        processThreadsCollectJob?.cancel()
        processThreadsCollectJob = null
        if (mBound) {
            unbindService(connection)
            mBound = false
        }
    }

    fun checkConfig() {
        val frpcDir = FrpType.FRPC.getDir(this)
        if (frpcDir.exists() && !frpcDir.isDirectory) {
            frpcDir.delete()
        }
        if (!frpcDir.exists()) frpcDir.mkdirs()
        val frpsDir = FrpType.FRPS.getDir(this)
        if (frpsDir.exists() && !frpsDir.isDirectory) {
            frpsDir.delete()
        }
        if (!frpsDir.exists()) frpsDir.mkdirs()
        // v1.1旧版本配置迁移
        // 遍历文件夹内的所有文件
        this.filesDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".toml")) {
                // 构建目标文件路径
                val destination = File(frpcDir, file.name)
                // 移动文件
                if (file.renameTo(destination)) {
                    Log.d("adx", "Moved: ${file.name} to ${destination.absolutePath}")
                } else {
                    Log.e("adx", "Failed to move: ${file.name}")
                }
            }
        }
    }

    private fun deleteConfig(config: FrpConfig) {
        val file = config.getFile(this)
        if (file.exists()) {
            file.delete()
        }
        updateConfigList()
    }

    private fun startConfigActivity(type: FrpType) {
        val currentDate = Date()
        val formatter = SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.getDefault())
        val formattedDateTime = formatter.format(currentDate)
        val fileName = "$formattedDateTime.toml"
        val file = File(type.getDir(this), fileName)
        file.writeBytes(resources.assets.open(type.getConfigAssetsName()).readBytes())
        val config = FrpConfig(type, fileName)
        startConfigActivity(config)
    }

    private fun startConfigActivity(config: FrpConfig) {
        val intent = Intent(this, ConfigActivity::class.java)
        intent.putExtra(IntentExtraKey.FrpConfig, config)
        configActivityLauncher.launch(intent)
    }

    private fun startShell(config: FrpConfig) {
        val intent = Intent(this, ShellService::class.java)
        intent.action = ShellServiceAction.START
        intent.putExtra(IntentExtraKey.FrpConfig, arrayListOf(config))
        startShellService(intent)
    }

    private fun stopShell(config: FrpConfig) {
        val intent = Intent(this, ShellService::class.java)
        intent.action = ShellServiceAction.STOP
        intent.putExtra(IntentExtraKey.FrpConfig, arrayListOf(config))
        startService(intent)
    }

    private fun autoStartOnAppLaunch() {
        val frpcSet = preferences.getStringSet(PreferencesKey.AUTO_START_ON_APP_LAUNCH_FRPC_LIST, emptySet())
        val frpsSet = preferences.getStringSet(PreferencesKey.AUTO_START_ON_APP_LAUNCH_FRPS_LIST, emptySet())
        val configs = mutableListOf<FrpConfig>()
        frpcSet?.forEach { configs.add(FrpConfig(FrpType.FRPC, it)) }
        frpsSet?.forEach { configs.add(FrpConfig(FrpType.FRPS, it)) }
        if (configs.isEmpty()) return

        // 过滤掉已经运行的配置
        val running = runningConfigList.value
        val toStart = configs.filter { !running.contains(it) }
        if (toStart.isEmpty()) return

        val intent = Intent(this, ShellService::class.java)
        intent.action = ShellServiceAction.START
        intent.putExtra(IntentExtraKey.FrpConfig, ArrayList(toStart))
        startShellService(intent)
    }

    /**
     * 检查并请求必要的运行时权限
     */
    private fun checkAndRequestPermissions() {
        // Android 13 及以上需要通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            permissionGranted.value = hasNotificationPermission

            if (!hasNotificationPermission) {
                // 检查是否应该显示权限说明
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    Log.d("adx", "Should show permission rationale")
                }
                // 请求权限
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // Android 13 以下不需要动态请求通知权限
            permissionGranted.value = true
        }
    }

    private fun createBGNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_MIN
            val channel = NotificationChannel("shell_bg", name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateConfigList() {
        configRefreshCounter.value++
        frpcConfigList.value = (FrpType.FRPC.getDir(this).list()?.toList() ?: listOf()).map {
            FrpConfig(FrpType.FRPC, it)
        }
        frpsConfigList.value = (FrpType.FRPS.getDir(this).list()?.toList() ?: listOf()).map {
            FrpConfig(FrpType.FRPS, it)
        }
        loadSettingsConfigList()

        // 检查自启动列表中是否含有已经删除的配置
        val frpcAutoStartList =
            preferences.getStringSet(PreferencesKey.AUTO_START_FRPC_LIST, emptySet())?.filter {
                frpcConfigList.value.contains(
                    FrpConfig(FrpType.FRPC, it)
                )
            }
        preferences.edit {
            putStringSet(PreferencesKey.AUTO_START_FRPC_LIST, frpcAutoStartList?.toSet())
        }
        val frpsAutoStartList =
            preferences.getStringSet(PreferencesKey.AUTO_START_FRPS_LIST, emptySet())?.filter {
                frpsConfigList.value.contains(
                    FrpConfig(FrpType.FRPS, it)
                )
            }
        preferences.edit {
            putStringSet(PreferencesKey.AUTO_START_FRPS_LIST, frpsAutoStartList?.toSet())
        }

        // 清理打开应用自动启动列表中已删除的配置
        val frpcAutoStartOnAppLaunchList =
            preferences.getStringSet(PreferencesKey.AUTO_START_ON_APP_LAUNCH_FRPC_LIST, emptySet())?.filter {
                frpcConfigList.value.contains(FrpConfig(FrpType.FRPC, it))
            }
        preferences.edit {
            putStringSet(PreferencesKey.AUTO_START_ON_APP_LAUNCH_FRPC_LIST, frpcAutoStartOnAppLaunchList?.toSet())
        }
        val frpsAutoStartOnAppLaunchList =
            preferences.getStringSet(PreferencesKey.AUTO_START_ON_APP_LAUNCH_FRPS_LIST, emptySet())?.filter {
                frpsConfigList.value.contains(FrpConfig(FrpType.FRPS, it))
            }
        preferences.edit {
            putStringSet(PreferencesKey.AUTO_START_ON_APP_LAUNCH_FRPS_LIST, frpsAutoStartOnAppLaunchList?.toSet())
        }
    }

    private suspend fun importZipFile(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        getString(R.string.import_failed_read),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                return@withContext
            }

            val zipInputStream = ZipInputStream(inputStream)
            var hasValidFolder = false
            var entry = zipInputStream.nextEntry

            while (entry != null) {
                val name = entry.name

                // 递归识别 frpc/frps 文件夹（大小写不敏感）
                // 支持任意深度的目录结构，如：backup/FRPC/config.toml 或 frpc/config.toml
                val (type, fileName) = findFrpTypeAndFileName(name)

                if (type != null && fileName != null && name.endsWith(".toml", ignoreCase = true)) {
                    hasValidFolder = true

                    // 生成唯一文件名（增量导入，避免覆盖）
                    val targetDir = type.getDir(this@MainActivity)
                    val uniqueFileName = generateUniqueFileName(targetDir, fileName)
                    val targetFile = File(targetDir, uniqueFileName)

                    // 写入文件
                    FileOutputStream(targetFile).use { output ->
                        zipInputStream.copyTo(output)
                    }
                }

                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }

            zipInputStream.close()

            if (!hasValidFolder) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        getString(R.string.import_failed_no_folder),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    updateConfigList()
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        getString(R.string.import_success),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    this@MainActivity,
                    getString(R.string.import_failed, e.message ?: "Unknown error"),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * 递归识别路径中的 frpc/frps 文件夹（大小写不敏感）
     * 支持任意深度的目录结构，如：
     * - FRPC/config.toml
     * - frpc/config.toml
     * - backup/FRPS/server.toml
     * - some/deep/path/frpc/client.toml
     *
     * @return Pair<FrpType?, String?> 如果找到有效的 frpc/frps 文件夹，返回类型和文件名；否则返回 null
     */
    private fun findFrpTypeAndFileName(path: String): Pair<FrpType?, String?> {
        val pathParts = path.split("/")

        // 从后向前遍历，找到最近的 frpc/frps 文件夹
        for (i in pathParts.indices.reversed()) {
            val part = pathParts[i].lowercase()
            when (part) {
                "frpc" -> {
                    // 取 frpc 文件夹之后的路径作为文件名
                    if (i < pathParts.size - 1) {
                        val fileName = pathParts.last()
                        return FrpType.FRPC to fileName
                    }
                }
                "frps" -> {
                    // 取 frps 文件夹之后的路径作为文件名
                    if (i < pathParts.size - 1) {
                        val fileName = pathParts.last()
                        return FrpType.FRPS to fileName
                    }
                }
            }
        }

        return null to null
    }

    private suspend fun importTomlFile(uri: Uri, type: FrpType) = withContext(Dispatchers.IO) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        getString(R.string.import_failed_read),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                return@withContext
            }

            // 获取文件名
            var fileName = getFileNameFromUri(uri) ?: "imported_${System.currentTimeMillis()}.toml"

            // 检查文件后缀
            if (!fileName.endsWith(".toml", ignoreCase = true)) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        getString(R.string.import_failed_not_toml),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                inputStream.close()
                return@withContext
            }

            // 生成唯一文件名
            val targetDir = type.getDir(this@MainActivity)
            val uniqueFileName = generateUniqueFileName(targetDir, fileName)
            val targetFile = File(targetDir, uniqueFileName)

            // 写入文件
            FileOutputStream(targetFile).use { output ->
                inputStream.copyTo(output)
            }

            inputStream.close()

            withContext(Dispatchers.Main) {
                updateConfigList()
                android.widget.Toast.makeText(
                    this@MainActivity,
                    getString(R.string.import_success),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    this@MainActivity,
                    getString(R.string.import_failed, e.message ?: "Unknown error"),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }

    private fun generateUniqueFileName(dir: File, fileName: String): String {
        var uniqueName = fileName
        var counter = 1

        while (File(dir, uniqueName).exists()) {
            val nameWithoutExt = fileName.substringBeforeLast(".")
            val ext = fileName.substringAfterLast(".")
            uniqueName = "${nameWithoutExt}_${counter}.$ext"
            counter++
        }

        return uniqueName
    }
}
