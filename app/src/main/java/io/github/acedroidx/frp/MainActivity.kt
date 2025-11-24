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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : ComponentActivity() {
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
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从 SharedPreferences 重新加载主题设置
        val savedTheme = preferences.getString(PreferencesKey.THEME_MODE, "跟随系统") ?: "跟随系统"
        themeMode.value = savedTheme

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
}