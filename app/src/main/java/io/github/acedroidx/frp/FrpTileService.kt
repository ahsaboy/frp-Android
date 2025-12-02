package io.github.acedroidx.frp

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class FrpTileService : TileService() {

    private var mService: ShellService? = null
    private var mBound: Boolean = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as ShellService.LocalBinder
            mService = binder.getService()
            mBound = true
            updateTileState()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mService = null
            mBound = false
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        // 绑定服务以获取运行状态
        val intent = Intent(this, ShellService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)
        updateTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
        if (mBound) {
            unbindService(connection)
            mBound = false
        }
    }

    override fun onClick() {
        super.onClick()

        val config = getSelectedConfig()
        if (config == null) {
            // 没有配置，打开设置页面让用户选择
            val intent = Intent(this, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivityAndCollapseCompat(intent)
            return
        }

        val isRunning = isConfigRunning(config)

        if (isRunning) {
            // 停止配置
            val intent = Intent(this, ShellService::class.java).apply {
                action = ShellServiceAction.STOP
                putExtra(IntentExtraKey.FrpConfig, arrayListOf(config))
            }
            startService(intent)
        } else {
            // 启动配置
            val intent = Intent(this, ShellService::class.java).apply {
                action = ShellServiceAction.START
                putExtra(IntentExtraKey.FrpConfig, arrayListOf(config))
            }
            startService(intent)
        }

        // 延迟更新状态，等待服务处理完成
        qsTile?.let { tile ->
            tile.state = if (isRunning) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            tile.updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startActivityAndCollapseCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val config = getSelectedConfig()

        if (config == null) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.quick_tile_not_configured)
            tile.icon = Icon.createWithResource(this, R.drawable.ic_launcher_foreground)
        } else {
            val isRunning = isConfigRunning(config)
            tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = config.fileName.removeSuffix(".toml")
            tile.icon = Icon.createWithResource(this, R.drawable.ic_launcher_foreground)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (isRunning) {
                    getString(R.string.quick_tile_running)
                } else {
                    getString(R.string.quick_tile_stopped)
                }
            }
        }

        tile.updateTile()
    }

    private fun getSelectedConfig(): FrpConfig? {
        val preferences = getSharedPreferences("data", MODE_PRIVATE)
        val configType = preferences.getString(PreferencesKey.QUICK_TILE_CONFIG_TYPE, null)
        val configName = preferences.getString(PreferencesKey.QUICK_TILE_CONFIG_NAME, null)

        if (configType == null || configName == null) {
            return null
        }

        val type = try {
            FrpType.valueOf(configType)
        } catch (_: IllegalArgumentException) {
            Log.e("FrpTileService", "Invalid config type: $configType")
            return null
        }

        // 检查配置文件是否存在
        val config = FrpConfig(type, configName)
        val file = config.getFile(this)
        if (!file.exists()) {
            Log.w("FrpTileService", "Config file does not exist: $configName")
            return null
        }

        return config
    }

    private fun isConfigRunning(config: FrpConfig): Boolean {
        return mService?.processThreads?.value?.containsKey(config) == true
    }
}
