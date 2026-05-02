package io.github.acedroidx.frp

import android.Manifest
import android.app.NotificationChannel
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
import android.os.IBinder
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
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
import io.github.acedroidx.frp.ui.theme.AppThemeMode
import io.github.acedroidx.frp.ui.theme.FrpTheme
import io.github.acedroidx.frp.ui.theme.readAppThemeMode
import io.github.acedroidx.frp.ui.theme.readUseMonet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import androidx.compose.ui.unit.DpSize
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme


class MainActivity : BaseActivity() {
    private val isStartup = MutableStateFlow(false)
    private val frpcConfigList = MutableStateFlow<List<FrpConfig>>(emptyList())
    private val frpsConfigList = MutableStateFlow<List<FrpConfig>>(emptyList())
    private val runningConfigList = MutableStateFlow<List<FrpConfig>>(emptyList())
    private val frpVersion = MutableStateFlow("Loading...")
    private val themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    private val useMonet = MutableStateFlow(false)
    private val permissionGranted = MutableStateFlow(true)
    private val logWrapEnabled = MutableStateFlow(true)

    private lateinit var preferences: SharedPreferences

    private lateinit var mService: ShellService
    private var mBound: Boolean = false
    private var processThreadsCollectJob: Job? = null

    private val showImportTypeDialog = mutableStateOf(false)
    private var pendingImportFile: Uri? = null
    private var appliedLanguagePreference: String = "system"

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

            // 获取frp版本
            lifecycleScope.launch {
                try {
                    val frpcVersion = mService.getFrpVersion(FrpType.FRPC)
                    val frpsVersion = mService.getFrpVersion(FrpType.FRPS)
                    val version = if (frpcVersion == frpsVersion) {
                        frpcVersion
                    } else {
                        "frpc:$frpcVersion/frps:$frpsVersion"
                    }
                    frpVersion.value = version
                    // 存储到 SharedPreferences
                    preferences.edit {
                        putString(PreferencesKey.FRP_VERSION, version)
                    }
                } catch (_: Exception) {
                    frpVersion.value = "Error"
                    preferences.edit {
                        putString(PreferencesKey.FRP_VERSION, "Error")
                    }
                }
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

        // 应用"最近任务中排除"设置
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
                Log.e("MainActivity", "Failed to set excludeFromRecents: ${e.message}")
            }
        }

        isStartup.value = preferences.getBoolean(PreferencesKey.AUTO_START, false)
        logWrapEnabled.value = preferences.getBoolean(PreferencesKey.LOG_WRAP_ENABLED, true)
        frpVersion.value = preferences.getString(PreferencesKey.FRP_VERSION, "Loading...") ?: "Loading..."
        themeMode.value = preferences.readAppThemeMode()
        useMonet.value = preferences.readUseMonet()
        appliedLanguagePreference = preferences.getString(PreferencesKey.APP_LANGUAGE, "system") ?: "system"

        checkConfig()
        updateConfigList()
        createBGNotificationChannel()
        checkAndRequestPermissions()

        applyEdgeToEdge()
        setContent {
        val navEventOwner = rememberNavigationEventDispatcherOwner(enabled = true, parent = null)
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides navEventOwner) {
            val currentTheme by themeMode.collectAsStateWithLifecycle(AppThemeMode.SYSTEM)
            val currentUseMonet by useMonet.collectAsStateWithLifecycle(false)
            val openDialog = remember { mutableStateOf(false) }
            val snackbarHostState = remember { SnackbarHostState() }
            val permissionGranted by permissionGranted.collectAsStateWithLifecycle(true)

            FrpTheme(themeMode = currentTheme, useMonet = currentUseMonet) {
                val frpVersion by frpVersion.collectAsStateWithLifecycle("Loading...")
                Scaffold(
                    topBar = {
                        SmallTopAppBar(
                            title = "${stringResource(R.string.frp_for_android)} - ${BuildConfig.VERSION_NAME}/$frpVersion",
                            actions = {
                                IconButton(onClick = {
                                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                                }) {
                                    Icon(
                                        imageVector = MiuixIcons.Settings,
                                        contentDescription = stringResource(R.string.settings_content_desc)
                                    )
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { openDialog.value = true }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Add,
                                contentDescription = stringResource(R.string.addConfigButton)
                            )
                        }
                    },
                    snackbarHost = {
                        SnackbarHost(state = snackbarHostState)
                    }
                ) { contentPadding ->
                    // Screen content
                    Box(
                        modifier = Modifier
                            .padding(contentPadding)
                            .verticalScroll(rememberScrollState())
                    ) {
                        MainContent()
                    }

                    if (openDialog.value) {
                        CreateConfigDialog { openDialog.value = false }
                    }

                    // 导入类型选择对话框
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
        }

        if (!mBound) {
            val intent = Intent(this, ShellService::class.java)
            bindService(intent, connection, BIND_AUTO_CREATE)
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun MainContent() {
        val frpcConfigList by frpcConfigList.collectAsStateWithLifecycle(emptyList())
        val frpsConfigList by frpsConfigList.collectAsStateWithLifecycle(emptyList())
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            if (frpcConfigList.isEmpty() && frpsConfigList.isEmpty()) {
                Text(
                    stringResource(R.string.no_config),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            if (frpcConfigList.isNotEmpty()) {
                Text("frpc", style = MiuixTheme.textStyles.headline1)
            }
            frpcConfigList.forEach { config -> FrpConfigItem(config) }
            if (frpsConfigList.isNotEmpty()) {
                Text("frps", style = MiuixTheme.textStyles.headline1)
            }
            frpsConfigList.forEach { config -> FrpConfigItem(config) }
        }
    }

    @Composable
    fun FrpConfigItem(config: FrpConfig) {
        val runningConfigList by runningConfigList.collectAsStateWithLifecycle(emptyList())
        val isLogWrapEnabled by logWrapEnabled.collectAsStateWithLifecycle(true)
        val isRunning = runningConfigList.contains(config)
        val showLog = remember { mutableStateOf(false) }
        val showDeleteDialog = remember { mutableStateOf(false) }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(config.fileName)
                        if (isRunning) {
                            Text(
                                stringResource(R.string.quick_tile_running),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.primary
                            )
                        }
                    }
                    Icon(
                        imageVector = if (showLog.value) {
                            MiuixIcons.ExpandLess
                        } else {
                            MiuixIcons.ExpandMore
                        },
                        contentDescription = stringResource(
                            if (showLog.value) {
                                R.string.collapse
                            } else {
                                R.string.expand
                            }
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    IconButton(
                        onClick = { startConfigActivity(config) },
                        enabled = !isRunning,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Edit,
                            contentDescription = stringResource(R.string.edit_config),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            showDeleteDialog.value = true
                        },
                        enabled = !isRunning,
                        modifier = Modifier.size(32.dp,28.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = stringResource(R.string.delete_config),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Switch(checked = isRunning, onCheckedChange = {
                        if (it) {
                            startShell(config)
                        } else {
                            stopShell(config)
                            showLog.value = false  // 关闭时自动收起日志
                        }
                    })
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
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            text = stringResource(R.string.dismiss),
                            onClick = { showDeleteDialog.value = false }
                        )
                        TextButton(
                            text = stringResource(R.string.deleteConfigButton),
                            onClick = {
                                deleteConfig(config)
                                showDeleteDialog.value = false
                            }
                        )
                    }
                }
            )
        }
    }

    @Composable
    @Preview(showBackground = true)
    fun CreateConfigDialog(onClose: () -> Unit = {}) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.create_frp_select),
            onDismissRequest = { onClose() },
            content = {
                // 创建配置按钮
                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(onClick = { startConfigActivity(FrpType.FRPC);onClose() }) {
                        Text("frpc")
                    }
                    Button(onClick = { startConfigActivity(FrpType.FRPS);onClose() }) {
                        Text("frps")
                    }
                }

                // 导入配置按钮
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.import_config),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MiuixTheme.textStyles.title2
                    )
                    Row(
                        horizontalArrangement = Arrangement.SpaceAround,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            text = stringResource(R.string.import_zip),
                            onClick = {
                                zipFileLauncher.launch("application/zip")
                                onClose()
                            }
                        )
                        TextButton(
                            text = stringResource(R.string.import_toml),
                            onClick = {
                                tomlFileLauncher.launch("*/*")
                                onClose()
                            }
                        )
                    }
                }
            }
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
        frpcConfigList.value = (FrpType.FRPC.getDir(this).list()?.toList() ?: listOf()).map {
            FrpConfig(FrpType.FRPC, it)
        }
        frpsConfigList.value = (FrpType.FRPS.getDir(this).list()?.toList() ?: listOf()).map {
            FrpConfig(FrpType.FRPS, it)
        }

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
