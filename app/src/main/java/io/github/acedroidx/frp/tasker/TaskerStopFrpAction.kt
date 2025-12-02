package io.github.acedroidx.frp.tasker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerActionNoOutput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperNoOutput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultError
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import io.github.acedroidx.frp.FrpConfig
import io.github.acedroidx.frp.FrpType
import io.github.acedroidx.frp.IntentExtraKey
import io.github.acedroidx.frp.PreferencesKey
import io.github.acedroidx.frp.R
import io.github.acedroidx.frp.ShellService
import io.github.acedroidx.frp.ShellServiceAction

/**
 * Input class for Tasker - defines which FRP configuration to stop
 * stopAll=true means stop all running configs, otherwise stop specific config
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
class ActivityConfigStopFrp : Activity(), TaskerPluginConfig<StopFrpInput> {
    override val context: Context get() = applicationContext

    private val taskerHelper by lazy { StopFrpHelper(this) }

    private lateinit var checkboxStopAll: CheckBox
    private lateinit var radioGroupType: RadioGroup
    private lateinit var spinnerConfig: Spinner
    private lateinit var textViewNoConfig: TextView
    private lateinit var buttonSave: Button
    private lateinit var buttonCancel: Button

    private var stopAll: Boolean = false
    private var selectedFrpType: FrpType = FrpType.FRPC
    private var configFiles: List<String> = emptyList()
    private var selectedConfigFile: String? = null

    override fun assignFromInput(input: TaskerInput<StopFrpInput>) {
        // Load previously saved configuration
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
        setContentView(R.layout.activity_tasker_config_stop)

        // Initialize views
        checkboxStopAll = findViewById(R.id.checkboxStopAll)
        radioGroupType = findViewById(R.id.radioGroupFrpType)
        spinnerConfig = findViewById(R.id.spinnerConfig)
        textViewNoConfig = findViewById(R.id.textViewNoConfig)
        buttonSave = findViewById(R.id.buttonSave)
        buttonCancel = findViewById(R.id.buttonCancel)

        // Load input from Tasker if editing existing action
        taskerHelper.onCreate()

        // Set initial checkbox state
        checkboxStopAll.isChecked = stopAll

        // Set up checkbox listener
        checkboxStopAll.setOnCheckedChangeListener { _, isChecked ->
            stopAll = isChecked
            if (isChecked) {
                // Hide specific config selection when "Stop All" is checked
                radioGroupType.visibility = View.GONE
                spinnerConfig.visibility = View.GONE
                textViewNoConfig.visibility = View.GONE
                buttonSave.isEnabled = true
            } else {
                // Show specific config selection
                radioGroupType.visibility = View.VISIBLE
                loadConfigFiles()
            }
        }

        // Set up radio group listener
        radioGroupType.setOnCheckedChangeListener { _, checkedId ->
            selectedFrpType = if (checkedId == R.id.radioFrps) {
                FrpType.FRPS
            } else {
                FrpType.FRPC
            }
            loadConfigFiles()
        }

        // Set initial selection based on loaded data
        if (selectedFrpType == FrpType.FRPS) {
            radioGroupType.check(R.id.radioFrps)
        } else {
            radioGroupType.check(R.id.radioFrpc)
        }

        // Load config files initially if not stopping all
        if (!stopAll) {
            loadConfigFiles()
        } else {
            radioGroupType.visibility = View.GONE
            spinnerConfig.visibility = View.GONE
            textViewNoConfig.visibility = View.GONE
        }

        // Set up buttons
        buttonSave.setOnClickListener {
            if (stopAll || selectedConfigFile != null) {
                taskerHelper.finishForTasker()
            }
        }

        buttonCancel.setOnClickListener {
            finish()
        }
    }

    private fun loadConfigFiles() {
        val dir = selectedFrpType.getDir(this)
        configFiles = dir.list()?.toList()?.sorted() ?: emptyList()

        if (configFiles.isEmpty()) {
            spinnerConfig.visibility = View.GONE
            textViewNoConfig.visibility = View.VISIBLE
            buttonSave.isEnabled = false
        } else {
            spinnerConfig.visibility = View.VISIBLE
            textViewNoConfig.visibility = View.GONE
            buttonSave.isEnabled = true

            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, configFiles)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerConfig.adapter = adapter

            // Set selected item if we have one
            if (selectedConfigFile != null && configFiles.contains(selectedConfigFile)) {
                val index = configFiles.indexOf(selectedConfigFile)
                spinnerConfig.setSelection(index)
            }

            spinnerConfig.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedConfigFile = configFiles[position]
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    selectedConfigFile = null
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
        // Check if Tasker is allowed
        val preferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
        val allowTasker = preferences.getBoolean(PreferencesKey.ALLOW_TASKER, true)

        if (!allowTasker) {
            return TaskerPluginResultError(
                Exception("Tasker integration is disabled. Please enable it in Settings.")
            )
        }

        // If stopAll is true, stop all running configs
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

        // Otherwise, stop specific config
        val frpTypeStr = input.regular.frpType
        val configFileName = input.regular.configFileName

        if (frpTypeStr.isNullOrBlank() || configFileName.isNullOrBlank()) {
            return TaskerPluginResultError(
                Exception("Invalid configuration: frpType and configFileName must be provided when stopAll is false")
            )
        }

        // Parse FRP type
        val frpType = when (frpTypeStr.lowercase()) {
            "frpc" -> FrpType.FRPC
            "frps" -> FrpType.FRPS
            else -> return TaskerPluginResultError(
                Exception("Invalid frpType: $frpTypeStr. Must be 'frpc' or 'frps'")
            )
        }

        // Create FrpConfig
        val config = FrpConfig(frpType, configFileName)

        // Check if config file exists
        val configFile = config.getFile(context)
        if (!configFile.exists()) {
            return TaskerPluginResultError(
                Exception("Configuration file not found: ${configFile.absolutePath}")
            )
        }

        // Stop the FRP service
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
