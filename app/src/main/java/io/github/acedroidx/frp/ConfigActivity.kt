package io.github.acedroidx.frp

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.acedroidx.frp.config.ui.ConfigFormScreen
import io.github.acedroidx.frp.ui.theme.AppThemeMode
import io.github.acedroidx.frp.ui.theme.FrpTheme
import io.github.acedroidx.frp.ui.theme.readAppThemeMode
import io.github.acedroidx.frp.ui.theme.readUseMonet
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class ConfigActivity : BaseActivity() {
    private val configEditText = MutableStateFlow("")
    private val isAutoStart = MutableStateFlow(false)
    private val isAutoStartOnAppLaunch = MutableStateFlow(false)
    private val frpVersion = MutableStateFlow("Loading...")
    private val themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    private val useMonet = MutableStateFlow(false)
    private lateinit var configFile: File
    private lateinit var frpConfigType: FrpType
    private lateinit var autoStartPreferencesKey: String
    private lateinit var autoStartOnAppLaunchPreferencesKey: String
    private lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val frpConfig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.extras?.getParcelable(IntentExtraKey.FrpConfig, FrpConfig::class.java)
        } else {
            @Suppress("DEPRECATION") intent?.extras?.getParcelable(IntentExtraKey.FrpConfig)
        }
        if (frpConfig == null) {
            Log.e("adx", "frp config is null")
            Toast.makeText(this, getString(R.string.config_error_missing), Toast.LENGTH_SHORT).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        configFile = frpConfig.getFile(this)
        frpConfigType = frpConfig.type
        autoStartPreferencesKey = frpConfig.type.getAutoStartPreferencesKey()
        autoStartOnAppLaunchPreferencesKey = frpConfig.type.getAutoStartOnAppLaunchPreferencesKey()
        preferences = getSharedPreferences("data", MODE_PRIVATE)
        frpVersion.value = preferences.getString(PreferencesKey.FRP_VERSION, "Loading...") ?: "Loading..."
        themeMode.value = preferences.readAppThemeMode()
        useMonet.value = preferences.readUseMonet()
        readConfig()
        readIsAutoStart()
        readIsAutoStartOnAppLaunch()

        applyEdgeToEdge()
        setContent {
        val navEventOwner = rememberNavigationEventDispatcherOwner(enabled = true, parent = null)
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides navEventOwner) {
            val currentTheme by themeMode.collectAsStateWithLifecycle(AppThemeMode.SYSTEM)
            val currentUseMonet by useMonet.collectAsStateWithLifecycle(false)
            val autoStart by isAutoStart.collectAsStateWithLifecycle(false)
            val autoStartOnAppLaunch by isAutoStartOnAppLaunch.collectAsStateWithLifecycle(false)
            FrpTheme(themeMode = currentTheme, useMonet = currentUseMonet) {
                ConfigFormScreen(
                    configType = frpConfigType,
                    initialToml = configEditText.value,
                    onSave = { newToml ->
                        configEditText.value = newToml
                        saveConfig()
                        closeActivity()
                    },
                    onCancel = { closeActivity() },
                    onDontSave = { closeActivity() },
                    configFileName = configFile.name.removeSuffix(".toml"),
                    onRename = { newName -> renameConfig(newName) },
                    isAutoStart = autoStart,
                    onAutoStartChange = { setAutoStart(it) },
                    isAutoStartOnAppLaunch = autoStartOnAppLaunch,
                    onAutoStartOnAppLaunchChange = { setAutoStartOnAppLaunch(it) },
                )
            }
            }
        }
    }

    fun readConfig() {
        if (configFile.exists()) {
            val mReader = configFile.bufferedReader()
            val mRespBuff = StringBuffer()
            val buff = CharArray(1024)
            var ch = 0
            while (mReader.read(buff).also { ch = it } != -1) {
                mRespBuff.append(buff, 0, ch)
            }
            mReader.close()
            configEditText.value = mRespBuff.toString()
        } else {
            Log.e("adx", "config file is not exist")
            Toast.makeText(this, getString(R.string.config_error_not_found), Toast.LENGTH_SHORT).show()
        }
    }

    fun saveConfig() {
        configFile.writeText(configEditText.value)
    }

    fun renameConfig(newName: String) {
        val originAutoStart = isAutoStart.value
        setAutoStart(false)
        val originAutoStartOnAppLaunch = isAutoStartOnAppLaunch.value
        setAutoStartOnAppLaunch(false)
        val newFile = File(configFile.parent, newName)
        configFile.renameTo(newFile)
        configFile = newFile
        setAutoStart(originAutoStart)
        setAutoStartOnAppLaunch(originAutoStartOnAppLaunch)
    }

    fun readIsAutoStart() {
        isAutoStart.value =
            preferences.getStringSet(autoStartPreferencesKey, emptySet())?.contains(configFile.name)
                ?: false
    }

    fun setAutoStart(value: Boolean) {
        val editor = preferences.edit()
        val set = preferences.getStringSet(autoStartPreferencesKey, emptySet())?.toMutableSet()
        if (value) {
            set?.add(configFile.name)
        } else {
            set?.remove(configFile.name)
        }
        editor.putStringSet(autoStartPreferencesKey, set)
        editor.apply()
        isAutoStart.value = value
    }

    fun readIsAutoStartOnAppLaunch() {
        isAutoStartOnAppLaunch.value =
            preferences.getStringSet(autoStartOnAppLaunchPreferencesKey, emptySet())?.contains(configFile.name)
                ?: false
    }

    fun setAutoStartOnAppLaunch(value: Boolean) {
        val editor = preferences.edit()
        val set = preferences.getStringSet(autoStartOnAppLaunchPreferencesKey, emptySet())?.toMutableSet()
        if (value) {
            set?.add(configFile.name)
        } else {
            set?.remove(configFile.name)
        }
        editor.putStringSet(autoStartOnAppLaunchPreferencesKey, set)
        editor.apply()
        isAutoStartOnAppLaunch.value = value
    }

    fun closeActivity() {
        setResult(RESULT_OK)
        finish()
    }
}
