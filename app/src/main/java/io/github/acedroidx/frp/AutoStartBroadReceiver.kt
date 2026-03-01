package io.github.acedroidx.frp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

class AutoStartBroadReceiver : BroadcastReceiver() {
    private val bootAction = Intent.ACTION_BOOT_COMPLETED
    private val packageReplacedAction = Intent.ACTION_MY_PACKAGE_REPLACED

    override fun onReceive(context: Context, intent: Intent) {
        val preferences = context.getSharedPreferences("data", AppCompatActivity.MODE_PRIVATE)
        val receivedAction = intent.action ?: return
        val keepAliveEnabled = preferences.getBoolean(PreferencesKey.KEEP_ALIVE_ENABLED, false)

        val shouldStart = when (receivedAction) {
            bootAction -> preferences.getBoolean(PreferencesKey.AUTO_START, false)
            packageReplacedAction -> {
                if (!keepAliveEnabled) {
                    false
                } else {
                    val frpcKeepAlive =
                        preferences.getStringSet(PreferencesKey.KEEP_ALIVE_FRPC_LIST, emptySet()).orEmpty()
                    val frpsKeepAlive =
                        preferences.getStringSet(PreferencesKey.KEEP_ALIVE_FRPS_LIST, emptySet()).orEmpty()
                    frpcKeepAlive.isNotEmpty() || frpsKeepAlive.isNotEmpty()
                }
            }
            else -> false
        }

        if (!shouldStart) {
            return
        }

        val frpcConfigSet = when (receivedAction) {
            bootAction -> preferences.getStringSet(PreferencesKey.AUTO_START_FRPC_LIST, emptySet())
            else -> preferences.getStringSet(PreferencesKey.KEEP_ALIVE_FRPC_LIST, emptySet())
        }
        val frpsConfigSet = when (receivedAction) {
            bootAction -> preferences.getStringSet(PreferencesKey.AUTO_START_FRPS_LIST, emptySet())
            else -> preferences.getStringSet(PreferencesKey.KEEP_ALIVE_FRPS_LIST, emptySet())
        }

        val frpcConfigList = frpcConfigSet?.map { FrpConfig(FrpType.FRPC, it) }
        val frpsConfigList = frpsConfigSet?.map { FrpConfig(FrpType.FRPS, it) }
        val configList = (frpsConfigList ?: emptyList()) + (frpcConfigList ?: emptyList())
        if (configList.isEmpty()) return

        val mainIntent = Intent(context, ShellService::class.java).apply {
            action = ShellServiceAction.START
            putParcelableArrayListExtra(IntentExtraKey.FrpConfig, ArrayList(configList))
        }
        context.startShellService(mainIntent)
    }
}
