package io.github.acedroidx.frp

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.BufferedReader
import java.io.FileReader
import java.util.concurrent.ConcurrentHashMap


class ShellService : LifecycleService() {
    companion object {
        private const val TAG = "ShellService"
        private const val PROCESS_RESTART_BASE_DELAY_MS = 2000L
        private const val PROCESS_RESTART_MAX_DELAY_MS = 60_000L
        private const val PROCESS_RESTART_MAX_RETRIES = 6
        private const val PROCESS_RESTART_STABLE_RUN_MS = 60_000L
        private const val DEFAULT_LOG_MAX_LINES = 20
        private const val MIN_LOG_MAX_LINES = 1
        private const val MAX_LOG_MAX_LINES = 500
        private const val LOG_FLUSH_DELAY_MS = 500L
    }

    private val _processThreads = MutableStateFlow(mutableMapOf<FrpConfig, ShellThread>())
    val processThreads = _processThreads.asStateFlow()

    private val _logText = MutableStateFlow("")

    // 为每个配置创建一个日志流
    private val _configLogs = MutableStateFlow(mutableMapOf<FrpConfig, String>())
    val configLogs = _configLogs.asStateFlow()
    private val preferences by lazy { getSharedPreferences("data", MODE_PRIVATE) }
    private val restartFailCount = ConcurrentHashMap<FrpConfig, Int>()
    private val processStartAtMs = ConcurrentHashMap<FrpConfig, Long>()
    private val logBufferLock = Any()
    private val configLogBuffers = mutableMapOf<FrpConfig, ArrayDeque<String>>()
    private val logFlushJobs = mutableMapOf<FrpConfig, Job>()

    fun clearConfigLog(config: FrpConfig) {
        synchronized(logBufferLock) {
            logFlushJobs.remove(config)?.cancel()
            configLogBuffers[config] = ArrayDeque()
            val logFile = config.getLogFile(this)
            if (logFile.exists()) {
                logFile.delete()
            }
        }
        _configLogs.update { currentLogs ->
            currentLogs.toMutableMap().apply {
                put(config, "")
            }
        }
    }

    fun getConfigLog(config: FrpConfig): String {
        return _configLogs.value[config] ?: run {
            val logContent = synchronized(logBufferLock) {
                val buffer = getOrLoadLogBufferLocked(config)
                buffer.joinToString("\n")
            }
            _configLogs.update { currentLogs ->
                currentLogs.toMutableMap().apply {
                    put(config, logContent)
                }
            }
            logContent
        }
    }

    fun getFrpVersion(type: FrpType): String {
        return try {
            val ainfo = packageManager.getApplicationInfo(
                packageName, PackageManager.GET_SHARED_LIBRARY_FILES
            )
            val command = listOf("${ainfo.nativeLibraryDir}/${type.getLibName()}", "-v")

            val processBuilder = ProcessBuilder(command)
            val process = processBuilder.start()

            val output = process.inputStream.bufferedReader().readText().trim()
            val errorOutput = process.errorStream.bufferedReader().readText().trim()

            process.waitFor()

            // frp版本信息通常在stdout或stderr中，优先使用stdout
            if (output.isNotEmpty()) {
                // 提取版本号，通常格式为 "frpc version x.x.x" 或类似格式
                val versionRegex = Regex("""(\d+\.\d+\.\d+)""")
                val match = versionRegex.find(output)
                match?.value ?: output.take(20) // 如果找不到版本号，返回前20个字符
            } else if (errorOutput.isNotEmpty()) {
                val versionRegex = Regex("""(\d+\.\d+\.\d+)""")
                val match = versionRegex.find(errorOutput)
                match?.value ?: errorOutput.take(20)
            } else {
                "Unknown"
            }
        } catch (e: Exception) {
            Log.e("adx", "Failed to get frp version: ${e.message}")
            "Error"
        }
    }

    private fun appendToConfigLog(config: FrpConfig, logLine: String) {
        val maxLines = getConfiguredLogMaxLines()
        val logContent: String
        synchronized(logBufferLock) {
            val buffer = getOrLoadLogBufferLocked(config)
            buffer.addLast(logLine)
            while (buffer.size > maxLines) {
                buffer.removeFirst()
            }
            logContent = buffer.joinToString("\n")
            scheduleLogFlushLocked(config)
        }
        _configLogs.update { currentLogs ->
            currentLogs.toMutableMap().apply {
                put(config, logContent)
            }
        }
    }

    private fun getOrLoadLogBufferLocked(config: FrpConfig): ArrayDeque<String> {
        val maxLines = getConfiguredLogMaxLines()
        val existing = configLogBuffers[config]
        if (existing != null) {
            while (existing.size > maxLines) {
                existing.removeFirst()
            }
            return existing
        }

        val buffer = ArrayDeque<String>()
        val logFile = config.getLogFile(this)
        if (logFile.exists()) {
            val lines = mutableListOf<String>()
            BufferedReader(FileReader(logFile)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    lines.add(line!!)
                }
            }
            lines.takeLast(maxLines).forEach { buffer.addLast(it) }
        }
        configLogBuffers[config] = buffer
        return buffer
    }

    private fun scheduleLogFlushLocked(config: FrpConfig) {
        val existingJob = logFlushJobs[config]
        if (existingJob != null && existingJob.isActive) {
            return
        }
        logFlushJobs[config] = lifecycleScope.launch {
            delay(LOG_FLUSH_DELAY_MS)
            flushConfigLogToDisk(config)
            synchronized(logBufferLock) {
                if (logFlushJobs[config] === this) {
                    logFlushJobs.remove(config)
                }
            }
        }
    }

    private fun flushConfigLogToDisk(config: FrpConfig) {
        val lines = synchronized(logBufferLock) {
            configLogBuffers[config]?.toList() ?: emptyList()
        }
        val logDir = config.getLogDir(this)
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        val logFile = config.getLogFile(this)
        FileWriter(logFile, false).use { writer ->
            lines.forEach { line ->
                writer.write(line)
                writer.write("\n")
            }
        }
    }

    private fun flushAllLogsImmediately() {
        val configs = synchronized(logBufferLock) {
            configLogBuffers.keys.toList()
        }
        configs.forEach { config ->
            flushConfigLogToDisk(config)
        }
    }

    // Binder given to clients
    private val binder = LocalBinder()

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder(), IBinder {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): ShellService = this@ShellService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent == null) {
            recoverDesiredConfigs("service recreated with null intent")
            return START_STICKY
        }

        // 处理 STOP_ALL action，不需要 frpConfig 参数
        if (intent.action == ShellServiceAction.STOP_ALL) {
            // 停止所有正在运行的配置
            val allConfigs = _processThreads.value.keys.toList()
            for (config in allConfigs) {
                stopFrp(config)
            }
            clearDesiredRunningConfigs()

            // 停止前台服务
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION") stopForeground(true)
            }
            stopSelf()

            Toast.makeText(this, "已停止所有配置", Toast.LENGTH_SHORT).show()

            return START_NOT_STICKY
        }

        val frpConfig: ArrayList<FrpConfig>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.extras?.getParcelableArrayList(
                    IntentExtraKey.FrpConfig, FrpConfig::class.java
                )
            } else {
                @Suppress("DEPRECATION") intent.extras?.getParcelableArrayList(IntentExtraKey.FrpConfig)
            }
        if (frpConfig == null) {
            Log.e("adx", "frpConfig is null")
            Toast.makeText(this, "frpConfig is null", Toast.LENGTH_SHORT).show()
            recoverDesiredConfigs("missing frpConfig extras")
            return START_STICKY
        }
        when (intent.action) {
            ShellServiceAction.START -> {
                startForeground(1, showNotification())
                if (isKeepAliveEnabled()) {
                    addDesiredRunningConfigs(frpConfig)
                }
                frpConfig.forEach { clearRestartState(it) }
                var runningChanged = false
                for (config in frpConfig) {
                    if (startFrp(config)) {
                        runningChanged = true
                    }
                }
                if (runningChanged || _processThreads.value.isNotEmpty()) {
                    Toast.makeText(this, getString(R.string.service_start_toast), Toast.LENGTH_SHORT)
                        .show()
                    startForeground(1, showNotification())
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION") stopForeground(true)
                    }
                    stopSelf()
                }
            }

            ShellServiceAction.STOP -> {
                removeDesiredRunningConfigs(frpConfig)
                for (config in frpConfig) {
                    stopFrp(config)
                }
                if (_processThreads.value.isNotEmpty()) {
                    startForeground(1, showNotification())
                }
                if (_processThreads.value.isEmpty()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION") stopForeground(true)
                    }
                    stopSelf()
                    Toast.makeText(this, getString(R.string.service_stop_toast), Toast.LENGTH_SHORT)
                        .show()
                }
            }

            else -> {
                Log.w(TAG, "Unknown action: ${intent.action}")
                recoverDesiredConfigs("unknown action")
            }
        }
        return START_STICKY
    }

    private fun startFrp(config: FrpConfig, showToast: Boolean = true): Boolean {
        Log.d("adx", "start config is $config")
        val dir = config.getDir(this)
        val file = config.getFile(this)
        if (!file.exists()) {
            Log.w("adx", "file is not exist,service won't start")
            if (showToast) {
                Toast.makeText(this, "file is not exist,service won't start", Toast.LENGTH_SHORT).show()
            }
            removeDesiredRunningConfigs(listOf(config))
            return false
        }
        if (_processThreads.value.contains(config)) {
            Log.w("adx", "frp is already running")
            if (showToast) {
                Toast.makeText(this, "frp is already running", Toast.LENGTH_SHORT).show()
            }
            return false
        }
        val ainfo = packageManager.getApplicationInfo(
            packageName, PackageManager.GET_SHARED_LIBRARY_FILES
        )
        val commandList =
            listOf("${ainfo.nativeLibraryDir}/${config.type.getLibName()}", "-c", config.fileName)
        Log.d("adx", "${dir}\n${commandList}")
        try {
            val thread = runCommand(commandList, dir, config)
            _processThreads.update { it.toMutableMap().apply { put(config, thread) } }
            processStartAtMs[config] = System.currentTimeMillis()
            return true
        } catch (e: Exception) {
            Log.e("adx", e.stackTraceToString())
            if (showToast) {
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            }
            stopSelf()
            return false
        }
    }

    private fun stopFrp(config: FrpConfig) {
        val thread = _processThreads.value[config]
//        thread?.interrupt()
        thread?.stopProcess()
        _processThreads.update {
            it.toMutableMap().apply { remove(config) }
        }
        processStartAtMs.remove(config)
    }

    override fun onDestroy() {
        super.onDestroy()
        flushAllLogsImmediately()
        synchronized(logBufferLock) {
            logFlushJobs.values.forEach { it.cancel() }
            logFlushJobs.clear()
        }
        if (!_processThreads.value.isEmpty()) {
            _processThreads.value.forEach {
//                it.value.interrupt()
                it.value.stopProcess()
            }
            _processThreads.update { it.clear();it }
        }
        synchronized(logBufferLock) {
            configLogBuffers.clear()
        }
        processStartAtMs.clear()
        restartFailCount.clear()
    }

    private fun runCommand(command: List<String>, dir: File, config: FrpConfig): ShellThread {
        lateinit var processThread: ShellThread
        processThread = ShellThread(
            command = command,
            dir = dir,
            outputCallback = { logLine ->
                _logText.value += logLine + "\n"
                appendToConfigLog(config, logLine)
            },
            onExitCallback = { exitCode, manuallyStopped ->
                onConfigProcessExit(config, processThread, exitCode, manuallyStopped)
            }
        )
        processThread.start()
        return processThread
    }

    private fun recoverDesiredConfigs(reason: String) {
        if (!isKeepAliveEnabled()) {
            return
        }
        val desiredConfigs = loadDesiredRunningConfigs(cleanInvalidEntries = true)
        if (desiredConfigs.isEmpty()) {
            return
        }

        var startedAny = false
        desiredConfigs.forEach { config ->
            if (startFrp(config, showToast = false)) {
                startedAny = true
            }
        }

        if (startedAny || _processThreads.value.isNotEmpty()) {
            Log.i(TAG, "Recovered ${_processThreads.value.size} config(s), reason=$reason")
            startForeground(1, showNotification())
        }
    }

    private fun onConfigProcessExit(
        config: FrpConfig,
        thread: ShellThread,
        exitCode: Int?,
        manuallyStopped: Boolean
    ) {
        var removed = false
        _processThreads.update { current ->
            current.toMutableMap().apply {
                if (this[config] === thread) {
                    remove(config)
                    removed = true
                }
            }
        }

        if (!removed) {
            return
        }

        val now = System.currentTimeMillis()
        val startedAt = processStartAtMs.remove(config)
        val runDurationMs = startedAt?.let { now - it } ?: 0L

        val desiredRunning = isKeepAliveEnabled() && isDesiredRunningConfig(config)
        if (!manuallyStopped && desiredRunning) {
            if (runDurationMs >= PROCESS_RESTART_STABLE_RUN_MS) {
                restartFailCount[config] = 0
            }
            val nextFailureCount = (restartFailCount[config] ?: 0) + 1
            restartFailCount[config] = nextFailureCount

            if (nextFailureCount > PROCESS_RESTART_MAX_RETRIES) {
                Log.e(
                    TAG,
                    "Keep-alive disabled for $config after $PROCESS_RESTART_MAX_RETRIES retries, lastExit=$exitCode"
                )
                appendToConfigLog(
                    config,
                    "Keep-alive paused: exceeded retry limit ($PROCESS_RESTART_MAX_RETRIES). Start manually to retry."
                )
                removeDesiredRunningConfigs(listOf(config))
                clearRestartState(config)
            } else {
                val retryDelayMs = calculateRestartDelayMs(nextFailureCount)
                appendToConfigLog(
                    config,
                    "Unexpected exit (code=$exitCode), retry $nextFailureCount/$PROCESS_RESTART_MAX_RETRIES in ${retryDelayMs}ms"
                )
                lifecycleScope.launch {
                    delay(retryDelayMs)
                    if (isKeepAliveEnabled() && isDesiredRunningConfig(config) && !_processThreads.value.contains(config)) {
                        Log.w(
                            TAG,
                            "Process exited unexpectedly (code=$exitCode), retry=$nextFailureCount, restarting: $config"
                        )
                        if (startFrp(config, showToast = false)) {
                            startForeground(1, showNotification())
                        }
                    }
                }
            }
        } else {
            clearRestartState(config)
        }

        if (_processThreads.value.isNotEmpty()) {
            startForeground(1, showNotification())
        } else if (isKeepAliveEnabled() && hasDesiredRunningConfigs()) {
            // 没有活动线程但仍有期望运行项，保持前台服务并等待自动重启
            startForeground(1, showNotification())
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION") stopForeground(true)
            }
            stopSelf()
        }
    }

    private fun addDesiredRunningConfigs(configs: List<FrpConfig>) {
        val frpcSet = getDesiredConfigNameSet(FrpType.FRPC)
        val frpsSet = getDesiredConfigNameSet(FrpType.FRPS)
        configs.forEach { config ->
            when (config.type) {
                FrpType.FRPC -> frpcSet.add(config.fileName)
                FrpType.FRPS -> frpsSet.add(config.fileName)
            }
        }
        saveDesiredConfigNameSet(FrpType.FRPC, frpcSet)
        saveDesiredConfigNameSet(FrpType.FRPS, frpsSet)
    }

    private fun removeDesiredRunningConfigs(configs: List<FrpConfig>) {
        val frpcSet = getDesiredConfigNameSet(FrpType.FRPC)
        val frpsSet = getDesiredConfigNameSet(FrpType.FRPS)
        configs.forEach { config ->
            when (config.type) {
                FrpType.FRPC -> frpcSet.remove(config.fileName)
                FrpType.FRPS -> frpsSet.remove(config.fileName)
            }
            clearRestartState(config)
        }
        saveDesiredConfigNameSet(FrpType.FRPC, frpcSet)
        saveDesiredConfigNameSet(FrpType.FRPS, frpsSet)
    }

    private fun clearDesiredRunningConfigs() {
        preferences.edit {
            remove(PreferencesKey.KEEP_ALIVE_FRPC_LIST)
            remove(PreferencesKey.KEEP_ALIVE_FRPS_LIST)
        }
        restartFailCount.clear()
        processStartAtMs.clear()
    }

    private fun hasDesiredRunningConfigs(): Boolean {
        return getDesiredConfigNameSet(FrpType.FRPC).isNotEmpty() ||
            getDesiredConfigNameSet(FrpType.FRPS).isNotEmpty()
    }

    private fun isDesiredRunningConfig(config: FrpConfig): Boolean {
        return getDesiredConfigNameSet(config.type).contains(config.fileName)
    }

    private fun loadDesiredRunningConfigs(cleanInvalidEntries: Boolean = false): List<FrpConfig> {
        val frpcNames = getDesiredConfigNameSet(FrpType.FRPC)
        val frpsNames = getDesiredConfigNameSet(FrpType.FRPS)

        val frpcConfigs = frpcNames.map { FrpConfig(FrpType.FRPC, it) }
        val frpsConfigs = frpsNames.map { FrpConfig(FrpType.FRPS, it) }
        val allConfigs = (frpcConfigs + frpsConfigs)

        if (!cleanInvalidEntries) {
            return allConfigs
        }

        val validConfigs = allConfigs.filter { it.getFile(this).exists() }
        if (validConfigs.size != allConfigs.size) {
            val validFrpcNames = validConfigs.filter { it.type == FrpType.FRPC }
                .map { it.fileName }
                .toSet()
            val validFrpsNames = validConfigs.filter { it.type == FrpType.FRPS }
                .map { it.fileName }
                .toSet()
            saveDesiredConfigNameSet(FrpType.FRPC, validFrpcNames)
            saveDesiredConfigNameSet(FrpType.FRPS, validFrpsNames)
        }
        return validConfigs
    }

    private fun getDesiredConfigNameSet(type: FrpType): MutableSet<String> {
        val key = getDesiredConfigKey(type)
        return preferences.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    private fun saveDesiredConfigNameSet(type: FrpType, names: Set<String>) {
        val key = getDesiredConfigKey(type)
        preferences.edit {
            putStringSet(key, names)
        }
    }

    private fun getDesiredConfigKey(type: FrpType): String {
        return when (type) {
            FrpType.FRPC -> PreferencesKey.KEEP_ALIVE_FRPC_LIST
            FrpType.FRPS -> PreferencesKey.KEEP_ALIVE_FRPS_LIST
        }
    }

    private fun calculateRestartDelayMs(failureCount: Int): Long {
        val exponent = (failureCount - 1).coerceAtLeast(0)
        val multiplier = 1L shl exponent.coerceAtMost(20)
        val delayMs = PROCESS_RESTART_BASE_DELAY_MS * multiplier
        return delayMs.coerceAtMost(PROCESS_RESTART_MAX_DELAY_MS)
    }

    private fun clearRestartState(config: FrpConfig) {
        restartFailCount.remove(config)
        processStartAtMs.remove(config)
    }

    private fun getConfiguredLogMaxLines(): Int {
        return preferences.getInt(PreferencesKey.LOG_MAX_LINES, DEFAULT_LOG_MAX_LINES)
            .coerceIn(MIN_LOG_MAX_LINES, MAX_LOG_MAX_LINES)
    }

    private fun isKeepAliveEnabled(): Boolean {
        return preferences.getBoolean(PreferencesKey.KEEP_ALIVE_ENABLED, false)
    }

    private fun showNotification(): Notification {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                // 检查是否需要从最近任务中排除
                val preferences = getSharedPreferences("data", MODE_PRIVATE)
                val excludeFromRecents = preferences.getBoolean(PreferencesKey.EXCLUDE_FROM_RECENTS, false)
                if (excludeFromRecents) {
                    notificationIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                }
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            }

        // 创建停止所有配置的 PendingIntent
        val stopAllIntent = Intent(this, ShellService::class.java).apply {
            action = ShellServiceAction.STOP_ALL
        }
        val stopAllPendingIntent = PendingIntent.getService(
            this,
            0,
            stopAllIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "shell_bg")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.frp_notification_title)).setContentText(
                getString(
                    R.string.frp_notification_content, _processThreads.value.size
                )
            )
            //.setTicker("test")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_baseline_delete_24,
                "停止",
                stopAllPendingIntent
            )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            notification.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        } else {
            notification.build()
        }
    }
}
