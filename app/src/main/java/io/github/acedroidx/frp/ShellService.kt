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
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileWriter
import java.io.BufferedReader
import java.io.FileReader
import java.util.Random


class ShellService : LifecycleService() {
    private val _processThreads = MutableStateFlow(mutableMapOf<FrpConfig, ShellThread>())
    val processThreads = _processThreads.asStateFlow()

    private val _logText = MutableStateFlow("")
    val logText: StateFlow<String> = _logText

    // 为每个配置创建一个日志流
    private val _configLogs = MutableStateFlow(mutableMapOf<FrpConfig, String>())
    val configLogs = _configLogs.asStateFlow()

    fun clearLog() {
        _logText.value = ""
    }

    fun clearConfigLog(config: FrpConfig) {
        val logFile = config.getLogFile(this)
        if (logFile.exists()) {
            logFile.delete()
        }
        // 清空内存中的日志
        _configLogs.update { currentLogs ->
            currentLogs.toMutableMap().apply {
                put(config, "")
            }
        }
    }

    fun getConfigLog(config: FrpConfig): String {
        // 优先返回内存中的实时日志
        return _configLogs.value[config] ?: run {
            // 如果内存中没有，从文件读取
            val logFile = config.getLogFile(this)
            if (!logFile.exists()) {
                return ""
            }

            val lines = mutableListOf<String>()
            BufferedReader(FileReader(logFile)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    lines.add(line!!)
                }
            }

            // 只返回最多20行
            val logContent = if (lines.size <= 20) {
                lines.joinToString("\n")
            } else {
                lines.takeLast(20).joinToString("\n")
            }

            // 同时更新内存中的日志
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
        val logDir = config.getLogDir(this)
        if (!logDir.exists()) {
            logDir.mkdirs()
        }

        val logFile = config.getLogFile(this)
        val lines = mutableListOf<String>()

        // 读取现有日志
        if (logFile.exists()) {
            BufferedReader(FileReader(logFile)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    lines.add(line!!)
                }
            }
        }

        // 添加新日志行
        lines.add(logLine)

        // 如果超过20行，删除最旧的行
        while (lines.size > 20) {
            lines.removeAt(0)
        }

        // 写回文件
        FileWriter(logFile, false).use { writer ->
            lines.forEach { line ->
                writer.write(line + "\n")
            }
        }

        // 实时更新内存中的日志流
        val logContent = lines.joinToString("\n")
        _configLogs.update { currentLogs ->
            currentLogs.toMutableMap().apply {
                put(config, logContent)
            }
        }
    }

    // Binder given to clients
    private val binder = LocalBinder()

    // Random number generator
    private val mGenerator = Random()

    /** method for clients  */
    val randomNumber: Int
        get() = mGenerator.nextInt(100)

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

        // 处理 STOP_ALL action，不需要 frpConfig 参数
        if (intent?.action == ShellServiceAction.STOP_ALL) {
            // 停止所有正在运行的配置
            val allConfigs = _processThreads.value.keys.toList()
            for (config in allConfigs) {
                stopFrp(config)
            }

            // 停止前台服务
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION") stopForeground(true)
            }
            stopSelf()

            Toast.makeText(this, "已停止所有配置", Toast.LENGTH_SHORT).show()

            // 关闭应用
            val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("EXIT_APP", true)
            }
            startActivity(mainActivityIntent)

            return START_NOT_STICKY
        }

        val frpConfig: ArrayList<FrpConfig>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent?.extras?.getParcelableArrayList(
                    IntentExtraKey.FrpConfig, FrpConfig::class.java
                )
            } else {
                @Suppress("DEPRECATION") intent?.extras?.getParcelableArrayList(IntentExtraKey.FrpConfig)
            }
        if (frpConfig == null) {
            Log.e("adx", "frpConfig is null")
            Toast.makeText(this, "frpConfig is null", Toast.LENGTH_SHORT).show()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ShellServiceAction.START -> {
                for (config in frpConfig) {
                    startFrp(config)
                }
                Toast.makeText(this, getString(R.string.service_start_toast), Toast.LENGTH_SHORT)
                    .show()
                startForeground(1, showNotification())
            }

            ShellServiceAction.STOP -> {
                for (config in frpConfig) {
                    stopFrp(config)
                }
                startForeground(1, showNotification())
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
        }
        return START_NOT_STICKY
    }

    private fun startFrp(config: FrpConfig) {
        Log.d("adx", "start config is $config")
        val dir = config.getDir(this)
        val file = config.getFile(this)
        if (!file.exists()) {
            Log.w("adx", "file is not exist,service won't start")
            Toast.makeText(this, "file is not exist,service won't start", Toast.LENGTH_SHORT).show()
            return
        }
        if (_processThreads.value.contains(config)) {
            Log.w("adx", "frp is already running")
            Toast.makeText(this, "frp is already running", Toast.LENGTH_SHORT).show()
            return
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
        } catch (e: Exception) {
            Log.e("adx", e.stackTraceToString())
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun stopFrp(config: FrpConfig) {
        val thread = _processThreads.value.get(config)
//        thread?.interrupt()
        thread?.stopProcess()
        _processThreads.update {
            it.toMutableMap().apply { remove(config) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!_processThreads.value.isEmpty()) {
            _processThreads.value.forEach {
//                it.value.interrupt()
                it.value.stopProcess()
            }
            _processThreads.update { it.clear();it }
        }
    }

    private fun runCommand(command: List<String>, dir: File, config: FrpConfig): ShellThread {
        val process_thread = ShellThread(command, dir) { logLine ->
            _logText.value += logLine + "\n"
            appendToConfigLog(config, logLine)
        }
        process_thread.start()
        return process_thread;
    }

    private fun showNotification(): Notification {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
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
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_baseline_delete_24,
                "停止",
                stopAllPendingIntent
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return notification.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        } else {
            return notification.build()
        }
    }
}