package io.github.acedroidx.frp

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.acedroidx.frp.ui.theme.FrpTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class ConfigActivity : ComponentActivity() {
    private val configEditText = MutableStateFlow("")
    private val isAutoStart = MutableStateFlow(false)
    private val frpVersion = MutableStateFlow("Loading...")
    private val themeMode = MutableStateFlow("跟随系统")
    private lateinit var configFile: File
    private lateinit var autoStartPreferencesKey: String
    private lateinit var preferences: SharedPreferences

    @OptIn(ExperimentalMaterial3Api::class)
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
        autoStartPreferencesKey = frpConfig.type.getAutoStartPreferencesKey()
        preferences = getSharedPreferences("data", MODE_PRIVATE)
        frpVersion.value = preferences.getString(PreferencesKey.FRP_VERSION, "Loading...") ?: "Loading..."
        themeMode.value = preferences.getString(PreferencesKey.THEME_MODE, "跟随系统") ?: "跟随系统"
        readConfig()
        readIsAutoStart()

        enableEdgeToEdge()
        setContent {
            val currentTheme by themeMode.collectAsStateWithLifecycle("跟随系统")
            FrpTheme(themeMode = currentTheme) {
                val frpVersion by frpVersion.collectAsStateWithLifecycle("Loading...")
                Scaffold(topBar = {
                    TopAppBar(
                        title = {
                            Text("frp for Android - ${BuildConfig.VERSION_NAME}/$frpVersion")
                        },
                        navigationIcon = {
                            IconButton(onClick = { closeActivity() }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_arrow_back_24dp),
                                    contentDescription = "返回"
                                )
                            }
                        }
                    )
                }) { contentPadding ->
                    // Screen content
                    MainContent(contentPadding)
                }
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun MainContent(contentPadding: PaddingValues = PaddingValues(0.dp)) {
        val openDialog = remember { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .imePadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Button(onClick = { saveConfig();closeActivity() }) {
                    Text(stringResource(R.string.saveConfigButton))
                }
                Button(onClick = { closeActivity() }) {
                    Text(stringResource(R.string.dontSaveConfigButton))
                }
                Button(onClick = { openDialog.value = true }) {
                    Text(stringResource(R.string.rename))
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Text(stringResource(R.string.auto_start_switch))
                Switch(checked = isAutoStart.collectAsStateWithLifecycle(false).value,
                    onCheckedChange = { setAutoStart(it) })
            }
            TextField(
                value = configEditText.collectAsStateWithLifecycle("").value,
                onValueChange = { configEditText.value = it },
                textStyle = MaterialTheme.typography.bodyMedium.merge(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .focusRequester(focusRequester)
            )
        }
        if (openDialog.value) {
            RenameDialog(configFile.name.removeSuffix(".toml")) { openDialog.value = false }
        }
    }

    @Composable
    fun RenameDialog(
        originName: String,
        onClose: () -> Unit,
    ) {
        var text by remember { mutableStateOf(originName) }
        AlertDialog(title = {
            Text(stringResource(R.string.rename))
        }, icon = {
            Icon(
                painterResource(id = R.drawable.ic_rename), contentDescription = "Rename Icon"
            )
        }, text = {
            TextField(text, onValueChange = { text = it })
        }, onDismissRequest = {
            onClose()
        }, confirmButton = {
            TextButton(onClick = {
                renameConfig("$text.toml")
                onClose()
            }) {
                Text(stringResource(R.string.confirm))
            }
        }, dismissButton = {
            TextButton(onClick = {
                onClose()
            }) {
                Text(stringResource(R.string.dismiss))
            }
        })
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