package io.github.acedroidx.frp

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.acedroidx.frp.config.ui.ConfigFormScreen
import io.github.acedroidx.frp.ui.theme.FrpTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class ConfigActivity : BaseActivity() {
    private val configEditText = MutableStateFlow("")
    private val isAutoStart = MutableStateFlow(false)
    private val frpVersion = MutableStateFlow("Loading...")
    private val themeMode = MutableStateFlow("跟随系统")
    private lateinit var configFile: File
    private lateinit var frpConfigType: FrpType
    private lateinit var autoStartPreferencesKey: String
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
            Toast.makeText(this, "frp config is null", Toast.LENGTH_SHORT).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        configFile = frpConfig.getFile(this)
        frpConfigType = frpConfig.type
        autoStartPreferencesKey = frpConfig.type.getAutoStartPreferencesKey()
        preferences = getSharedPreferences("data", MODE_PRIVATE)
        frpVersion.value = preferences.getString(PreferencesKey.FRP_VERSION, "Loading...") ?: "Loading..."
        themeMode.value = preferences.getString(PreferencesKey.THEME_MODE, "跟随系统") ?: "跟随系统"
        readConfig()
        readIsAutoStart()

        enableEdgeToEdge()
        setContent {
            val currentTheme by themeMode.collectAsStateWithLifecycle("跟随系统")
            val autoStart by isAutoStart.collectAsStateWithLifecycle(false)
            FrpTheme(themeMode = currentTheme) {
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
                )
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
            Toast.makeText(this, "config file is not exist", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveConfig() {
        configFile.writeText(configEditText.value)
    }

    fun renameConfig(newName: String) {
        val originAutoStart = isAutoStart.value
        setAutoStart(false)
        val newFile = File(configFile.parent, newName)
        configFile.renameTo(newFile)
        configFile = newFile
        setAutoStart(originAutoStart)
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

    fun closeActivity() {
        setResult(RESULT_OK)
        finish()
    }
}
