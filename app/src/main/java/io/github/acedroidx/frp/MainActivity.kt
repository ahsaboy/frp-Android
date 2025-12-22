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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.core.content.edit
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import io.github.acedroidx.frp.ui.theme.FrpTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.OutlinedButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream


class MainActivity : BaseActivity() {
    private val isStartup = MutableStateFlow(false)
    private val frpcConfigList = MutableStateFlow<List<FrpConfig>>(emptyList())
    private val frpsConfigList = MutableStateFlow<List<FrpConfig>>(emptyList())
    private val runningConfigList = MutableStateFlow<List<FrpConfig>>(emptyList())
    private val frpVersion = MutableStateFlow("Loading...")
    private val themeMode = MutableStateFlow("跟随系统")
    private val permissionGranted = MutableStateFlow(true)

    private lateinit var preferences: SharedPreferences

    private lateinit var mService: ShellService
    private var mBound: Boolean = false

    private val showImportTypeDialog = mutableStateOf(false)
    private var pendingImportFile: Uri? = null

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

            mService.lifecycleScope.launch {
                mService.processThreads.collect { processThreads ->
                    runningConfigList.value = processThreads.keys.toList()
                }
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    private val configActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
            updateConfigList()
        }

    @OptIn(ExperimentalMaterial3Api::class)
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
        frpVersion.value = preferences.getString(PreferencesKey.FRP_VERSION, "Loading...") ?: "Loading..."
        themeMode.value = preferences.getString(PreferencesKey.THEME_MODE, "跟随系统") ?: "跟随系统"

        checkConfig()
        updateConfigList()
        createBGNotificationChannel()
        checkAndRequestPermissions()

        enableEdgeToEdge()
        setContent {
            val currentTheme by themeMode.collectAsStateWithLifecycle("跟随系统")
            val openDialog = remember { mutableStateOf(false) }
            val snackbarHostState = remember { SnackbarHostState() }
            val permissionGranted by permissionGranted.collectAsStateWithLifecycle(true)

            FrpTheme(themeMode = currentTheme) {
                val frpVersion by frpVersion.collectAsStateWithLifecycle("Loading...")
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text("frp for Android - ${BuildConfig.VERSION_NAME}/$frpVersion")
                            },
                            actions = {
                                IconButton(onClick = {
                                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                                }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_settings_24dp),
                                        contentDescription = "设置"
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
                                painter = painterResource(id = android.R.drawable.ic_input_add),
                                contentDescription = stringResource(R.string.addConfigButton)
                            )
                        }
                    },
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState)
                    }
                ) { contentPadding ->
                    // Screen content
                    Box(
                        modifier = Modifier
                            .padding(contentPadding)
                            .verticalScroll(rememberScrollState())
                            .scrollable(
                                orientation = Orientation.Vertical,
                                state = rememberScrollableState { delta -> 0f })
                    ) {
                        MainContent()
                    }
                }
                if (openDialog.value) {
                    CreateConfigDialog { openDialog.value = false }
                }

                // 导入类型选择对话框
                if (showImportTypeDialog.value) {
                    ImportTypeDialog { showImportTypeDialog.value = false }
                }

                // 显示权限提示
                val scope = rememberCoroutineScope()
                LaunchedEffect(permissionGranted) {
                    if (!permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "通知权限未授予，后台运行通知将无法显示",
                                actionLabel = "去设置",
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
                Text("frpc", style = MaterialTheme.typography.titleLarge)
            }
            frpcConfigList.forEach { config -> FrpConfigItem(config) }
            if (frpsConfigList.isNotEmpty()) {
                Text("frps", style = MaterialTheme.typography.titleLarge)
            }
            frpsConfigList.forEach { config -> FrpConfigItem(config) }
        }
    }

    @Composable
    fun FrpConfigItem(config: FrpConfig) {
        val runningConfigList by runningConfigList.collectAsStateWithLifecycle(emptyList())
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
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
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
                                "运行中",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = if (showLog.value) "▲" else "▼",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(
                        onClick = { startConfigActivity(config) },
                        enabled = !isRunning,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pencil_24dp),
                            contentDescription = "编辑",
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
                            painter = painterResource(id = R.drawable.ic_baseline_delete_24),
                            contentDescription = "删除配置",
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
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(8.dp)
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
                                "配置日志",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Button(
                                onClick = {
                                    if (mBound) {
                                        mService.clearConfigLog(config)
                                    }
                                },
                                modifier = Modifier.size(width = 80.dp, height = 35.dp)
                            ) {
                                Text(
                                    "清除",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        SelectionContainer {
                            Text(
                                text = configLog.ifEmpty {
                                    "暂无日志"
                                },
                                style = MaterialTheme.typography.bodySmall.merge(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 删除确认对话框
        if (showDeleteDialog.value) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog.value = false },
                title = { Text("确认删除") },
                text = { Text("确认删除 ${config.fileName} 吗？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            deleteConfig(config)
                            showDeleteDialog.value = false
                        }
                    ) {
                        Text("删除")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteDialog.value = false }
                    ) {
                        Text("取消")
                    }
                }
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    @Preview(showBackground = true)
    fun CreateConfigDialog(onClose: () -> Unit = {}) {
        BasicAlertDialog(onDismissRequest = { onClose() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        stringResource(R.string.create_frp_select),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge
                    )
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
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceAround,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(onClick = {
                                zipFileLauncher.launch("application/zip")
                                onClose()
                            }) {
                                Text(stringResource(R.string.import_zip))
                            }
                            OutlinedButton(onClick = {
                                tomlFileLauncher.launch("*/*")
                                onClose()
                            }) {
                                Text(stringResource(R.string.import_toml))
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ImportTypeDialog(onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.import_select_type)) },
            text = { Text(stringResource(R.string.import_select_type_desc)) },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = {
                        pendingImportFile?.let { uri ->
                            lifecycleScope.launch {
                                importTomlFile(uri, FrpType.FRPC)
                            }
                        }
                        onDismiss()
                    }) {
                        Text("FRPC")
                    }
                    TextButton(onClick = {
                        pendingImportFile?.let { uri ->
                            lifecycleScope.launch {
                                importTomlFile(uri, FrpType.FRPS)
                            }
                        }
                        onDismiss()
                    }) {
                        Text("FRPS")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        // 从 SharedPreferences 重新加载主题设置
        val savedTheme = preferences.getString(PreferencesKey.THEME_MODE, "跟随系统") ?: "跟随系统"
        themeMode.value = savedTheme

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
        startService(intent)
    }

    private fun stopShell(config: FrpConfig) {
        val intent = Intent(this, ShellService::class.java)
        intent.action = ShellServiceAction.STOP
        intent.putExtra(IntentExtraKey.FrpConfig, arrayListOf(config))
        startService(intent)
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