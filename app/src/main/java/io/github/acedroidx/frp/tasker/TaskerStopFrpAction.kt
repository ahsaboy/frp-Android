package io.github.acedroidx.frp.tasker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerActionNoOutput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperNoOutput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultError
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import io.github.acedroidx.frp.BaseActivity
import io.github.acedroidx.frp.FrpConfig
import io.github.acedroidx.frp.FrpType
import io.github.acedroidx.frp.IntentExtraKey
import io.github.acedroidx.frp.PreferencesKey
import io.github.acedroidx.frp.R
import io.github.acedroidx.frp.ShellService
import io.github.acedroidx.frp.ShellServiceAction
import io.github.acedroidx.frp.ui.theme.AppThemeMode
import io.github.acedroidx.frp.ui.theme.FrpTheme
import io.github.acedroidx.frp.ui.theme.readAppThemeMode
import io.github.acedroidx.frp.ui.theme.readUseMonet
import kotlinx.coroutines.flow.MutableStateFlow
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference

/**
 * Input class for Tasker - defines which FRP configuration to stop
 */
@TaskerInputRoot
class StopFrpInput @JvmOverloads constructor(
    @field:TaskerInputField("stopAll") var stopAll: Boolean = false,
    @field:TaskerInputField("frpType") var frpType: String? = null,
    @field:TaskerInputField("configFileName") var configFileName: String? = null
)

/**
 * Config Helper - manages the configuration UI and validation
 */
class StopFrpHelper(config: TaskerPluginConfig<StopFrpInput>) :
    TaskerPluginConfigHelperNoOutput<StopFrpInput, StopFrpRunner>(config) {

    override val runnerClass: Class<StopFrpRunner> get() = StopFrpRunner::class.java
    override val inputClass: Class<StopFrpInput> get() = StopFrpInput::class.java

    override fun addToStringBlurb(input: TaskerInput<StopFrpInput>, blurbBuilder: StringBuilder) {
        if (input.regular.stopAll) {
            blurbBuilder.append("Stop all FRP configs")
        } else {
            val frpType = input.regular.frpType ?: "frpc"
            val fileName = input.regular.configFileName ?: "default"
            blurbBuilder.append("Stop [$frpType] $fileName")
        }
    }
}

/**
 * Config Activity - the UI that appears when configuring the Tasker action
 */
class ActivityConfigStopFrp : BaseActivity(), TaskerPluginConfig<StopFrpInput> {
    override val context: Context get() = applicationContext

    private val taskerHelper by lazy { StopFrpHelper(this) }

    private var stopAll: Boolean = false
    private var selectedFrpType: FrpType = FrpType.FRPC
    private var configFiles: List<String> = emptyList()
    private var selectedConfigFile: String? = null

    private val themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    private val useMonet = MutableStateFlow(false)

    override fun assignFromInput(input: TaskerInput<StopFrpInput>) {
        stopAll = input.regular.stopAll

        val frpType = input.regular.frpType
        val configFileName = input.regular.configFileName

        if (!frpType.isNullOrBlank() && frpType != "%frpType") {
            selectedFrpType = when (frpType.lowercase()) {
                "frps" -> FrpType.FRPS
                else -> FrpType.FRPC
            }
        }

        if (!configFileName.isNullOrBlank() && configFileName != "%configFileName") {
            selectedConfigFile = configFileName
        }
    }

    override val inputForTasker: TaskerInput<StopFrpInput>
        get() {
            return if (stopAll) {
                TaskerInput(StopFrpInput(stopAll = true))
            } else {
                val frpType = selectedFrpType.typeName
                val configFileName = selectedConfigFile ?: ""
                TaskerInput(StopFrpInput(stopAll = false, frpType = frpType, configFileName = configFileName))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferences = getSharedPreferences("data", MODE_PRIVATE)
        themeMode.value = preferences.readAppThemeMode()
        useMonet.value = preferences.readUseMonet()

        taskerHelper.onCreate()

        applyEdgeToEdge()
        setContent {
            val navEventOwner = rememberNavigationEventDispatcherOwner(enabled = true, parent = null)
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides navEventOwner) {
                val currentTheme by themeMode.collectAsStateWithLifecycle(AppThemeMode.SYSTEM)
                val currentUseMonet by useMonet.collectAsStateWithLifecycle(false)
                FrpTheme(themeMode = currentTheme, useMonet = currentUseMonet) {
                    TaskerStopFrpContent()
                }
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    private fun TaskerStopFrpContent() {
        var isStopAll by remember { mutableStateOf(stopAll) }
        var frpTypeIndex by remember { mutableStateOf(if (selectedFrpType == FrpType.FRPS) 1 else 0) }
        var configFileIndex by remember { mutableStateOf(-1) }
        var loadedConfigFiles by remember { mutableStateOf(emptyList<String>()) }

        LaunchedEffect(frpTypeIndex) {
            selectedFrpType = if (frpTypeIndex == 1) FrpType.FRPS else FrpType.FRPC
            val dir = selectedFrpType.getDir(this@ActivityConfigStopFrp)
            loadedConfigFiles = dir.list()?.toList()?.sorted() ?: emptyList()

            configFileIndex = if (selectedConfigFile != null && loadedConfigFiles.contains(selectedConfigFile)) {
                loadedConfigFiles.indexOf(selectedConfigFile)
            } else {
                -1
            }

            if (configFileIndex >= 0) {
                selectedConfigFile = loadedConfigFiles[configFileIndex]
            }
        }

        Scaffold(
            topBar = {
                SmallTopAppBar(
                    title = stringResource(R.string.tasker_stop_config_title),
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_arrow_back_24dp),
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
            },
            bottomBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.dismiss),
                        onClick = { finish() },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            if (isStopAll || selectedConfigFile != null) {
                                taskerHelper.finishForTasker()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = isStopAll || loadedConfigFiles.isNotEmpty(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(stringResource(R.string.saveConfigButton))
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = stringResource(R.string.tasker_stop_all),
                        summary = null,
                        onClick = {
                            isStopAll = !isStopAll
                            stopAll = isStopAll
                        },
                        endActions = {
                            Switch(
                                checked = isStopAll,
                                onCheckedChange = {
                                    isStopAll = it
                                    stopAll = it
                                },
                            )
                        },
                    )
                }

                if (!isStopAll) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        OverlaySpinnerPreference(
                            title = stringResource(R.string.tasker_select_type),
                            items = listOf(
                                SpinnerEntry(title = "frpc"),
                                SpinnerEntry(title = "frps"),
                            ),
                            selectedIndex = frpTypeIndex,
                            onSelectedIndexChange = { frpTypeIndex = it },
                        )
                    }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        OverlaySpinnerPreference(
                            title = stringResource(R.string.tasker_select_config),
                            items = if (loadedConfigFiles.isNotEmpty()) {
                                loadedConfigFiles.map { SpinnerEntry(title = it) }
                            } else {
                                listOf(SpinnerEntry(title = stringResource(R.string.tasker_no_config)))
                            },
                            selectedIndex = configFileIndex.coerceAtLeast(0),
                            onSelectedIndexChange = { idx ->
                                configFileIndex = idx
                                selectedConfigFile = loadedConfigFiles.getOrNull(idx)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Runner - executes the actual action when Tasker calls it
 */
class StopFrpRunner : TaskerPluginRunnerActionNoOutput<StopFrpInput>() {
    override fun run(context: Context, input: TaskerInput<StopFrpInput>): TaskerPluginResult<Unit> {
        val preferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
        val allowTasker = preferences.getBoolean(PreferencesKey.ALLOW_TASKER, true)

        if (!allowTasker) {
            return TaskerPluginResultError(
                Exception("Tasker integration is disabled. Please enable it in Settings.")
            )
        }

        if (input.regular.stopAll) {
            val intent = Intent(context, ShellService::class.java).apply {
                action = ShellServiceAction.STOP_ALL
            }

            try {
                context.startService(intent)
                return TaskerPluginResultSucess()
            } catch (e: Exception) {
                return TaskerPluginResultError(e)
            }
        }

        val frpTypeStr = input.regular.frpType
        val configFileName = input.regular.configFileName

        if (frpTypeStr.isNullOrBlank() || configFileName.isNullOrBlank()) {
            return TaskerPluginResultError(
                Exception("Invalid configuration: frpType and configFileName must be provided when stopAll is false")
            )
        }

        val frpType = when (frpTypeStr.lowercase()) {
            "frpc" -> FrpType.FRPC
            "frps" -> FrpType.FRPS
            else -> return TaskerPluginResultError(
                Exception("Invalid frpType: $frpTypeStr. Must be 'frpc' or 'frps'")
            )
        }

        val config = FrpConfig(frpType, configFileName)

        val configFile = config.getFile(context)
        if (!configFile.exists()) {
            return TaskerPluginResultError(
                Exception("Configuration file not found: ${configFile.absolutePath}")
            )
        }

        val intent = Intent(context, ShellService::class.java).apply {
            action = ShellServiceAction.STOP
            putExtra(IntentExtraKey.FrpConfig, arrayListOf(config))
        }

        try {
            context.startService(intent)
            return TaskerPluginResultSucess()
        } catch (e: Exception) {
            return TaskerPluginResultError(e)
        }
    }
}
