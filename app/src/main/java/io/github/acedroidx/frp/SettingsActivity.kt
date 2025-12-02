package io.github.acedroidx.frp

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.acedroidx.frp.ui.theme.FrpTheme
import kotlinx.coroutines.flow.MutableStateFlow

class SettingsActivity : ComponentActivity() {
    private val isStartup = MutableStateFlow(false)
    private val themeMode = MutableStateFlow("跟随系统")
    private val allowTasker = MutableStateFlow(true)
    private val excludeFromRecents = MutableStateFlow(false)
    private lateinit var preferences: SharedPreferences

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = getSharedPreferences("data", MODE_PRIVATE)
        isStartup.value = preferences.getBoolean(PreferencesKey.AUTO_START, false)

        // 读取主题设置，默认为跟随系统
        val savedTheme = preferences.getString(PreferencesKey.THEME_MODE, "跟随系统") ?: "跟随系统"
        themeMode.value = savedTheme

        // 读取 Tasker 权限设置，默认为允许
        allowTasker.value = preferences.getBoolean(PreferencesKey.ALLOW_TASKER, true)

        // 读取"最近任务中排除"设置，默认为不排除
        excludeFromRecents.value = preferences.getBoolean(PreferencesKey.EXCLUDE_FROM_RECENTS, false)

        enableEdgeToEdge()
        setContent {
            val currentTheme by themeMode.collectAsStateWithLifecycle("跟随系统")
            FrpTheme(themeMode = currentTheme) {
                Scaffold(topBar = {
                    TopAppBar(
                        title = {
                            Text("设置")
                        },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_arrow_back_24dp),
                                    contentDescription = "返回"
                                )
                            }
                        }
                    )
                }) { contentPadding ->
                    Box(
                        modifier = Modifier
                            .padding(contentPadding)
                            .verticalScroll(rememberScrollState())
                    ) {
                        SettingsContent()
                    }
                }
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun SettingsContent() {
        val isAutoStart by isStartup.collectAsStateWithLifecycle(false)
        val currentTheme by themeMode.collectAsStateWithLifecycle("跟随系统")
        val isTaskerAllowed by allowTasker.collectAsStateWithLifecycle(true)
        val isExcludeFromRecents by excludeFromRecents.collectAsStateWithLifecycle(false)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // 开机自启动设置项
            SettingItemWithSwitch(
                title = "开机自启动",
                checked = isAutoStart,
                onCheckedChange = { checked ->
                    val editor = preferences.edit()
                    editor.putBoolean(PreferencesKey.AUTO_START, checked)
                    editor.apply()
                    isStartup.value = checked
                }
            )

            HorizontalDivider()

            // 主题切换设置项
            SettingItemWithDropdown(
                title = "主题模式",
                currentValue = currentTheme,
                options = listOf("深色", "浅色", "跟随系统"),
                onValueChange = { newTheme ->
                    val editor = preferences.edit()
                    editor.putString(PreferencesKey.THEME_MODE, newTheme)
                    editor.apply()
                    themeMode.value = newTheme
                }
            )

            HorizontalDivider()

            // 允许 Tasker 调用设置项
            SettingItemWithSwitch(
                title = "允许 Tasker 调用",
                checked = isTaskerAllowed,
                onCheckedChange = { checked ->
                    val editor = preferences.edit()
                    editor.putBoolean(PreferencesKey.ALLOW_TASKER, checked)
                    editor.apply()
                    allowTasker.value = checked
                }
            )

            HorizontalDivider()

            // 最近任务中排除设置项
            SettingItemWithSwitch(
                title = "最近任务中排除",
                checked = isExcludeFromRecents,
                onCheckedChange = { checked ->
                    val editor = preferences.edit()
                    editor.putBoolean(PreferencesKey.EXCLUDE_FROM_RECENTS, checked)
                    editor.apply()
                    excludeFromRecents.value = checked

                    // 立即应用设置，不需要重启
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        try {
                            val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                            val appTasks = am.appTasks
                            android.util.Log.d("SettingsActivity", "appTasks size: ${appTasks.size}")
                            if (appTasks.isNotEmpty()) {
                                for (task in appTasks) {
                                    task.setExcludeFromRecents(checked)
                                    android.util.Log.d("SettingsActivity", "Set excludeFromRecents to $checked")
                                }
                            } else {
                                android.util.Log.w("SettingsActivity", "appTasks is empty")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SettingsActivity", "Failed to set excludeFromRecents: ${e.message}")
                        }
                    }
                }
            )

            HorizontalDivider()

            // 关于设置项
            SettingItemClickable(
                title = "关于",
                onClick = {
                    startActivity(Intent(this@SettingsActivity, AboutActivity::class.java))
                }
            )
        }
    }

    @Composable
    fun SettingItemWithSwitch(
        title: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }

    @Composable
    fun SettingItemWithDropdown(
        title: String,
        currentValue: String,
        options: List<String>,
        onValueChange: (String) -> Unit
    ) {
        var expanded by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Box {
                Text(
                    text = currentValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onValueChange(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun SettingItemClickable(
        title: String,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_back_24dp),
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
