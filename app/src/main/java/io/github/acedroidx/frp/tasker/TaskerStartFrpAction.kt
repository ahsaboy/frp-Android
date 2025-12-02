package io.github.acedroidx.frp.tasker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
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
 * Input class for Tasker - defines which FRP configuration to start
 */
@TaskerInputRoot
class StartFrpInput @JvmOverloads constructor(
    @field:TaskerInputField("frpType") var frpType: String? = null,
    @field:TaskerInputField("configFileName") var configFileName: String? = null
)

/**
 * Config Helper - manages the configuration UI and validation
 */
class StartFrpHelper(config: TaskerPluginConfig<StartFrpInput>) :
    TaskerPluginConfigHelperNoOutput<StartFrpInput, StartFrpRunner>(config) {

    override val runnerClass: Class<StartFrpRunner> get() = StartFrpRunner::class.java
    override val inputClass: Class<StartFrpInput> get() = StartFrpInput::class.java

    override fun addToStringBlurb(input: TaskerInput<StartFrpInput>, blurbBuilder: StringBuilder) {
        val frpType = input.regular.frpType ?: "frpc"
        val fileName = input.regular.configFileName ?: "default"
        blurbBuilder.append("\nStart [$frpType] $fileName")
    }
}

/**
 * Config Activity - the UI that appears when configuring the Tasker action
 */
class ActivityConfigStartFrp : Activity(), TaskerPluginConfig<StartFrpInput> {
    override val context: Context get() = applicationContext

    private val taskerHelper by lazy { StartFrpHelper(this) }

    private lateinit var radioGroupType: RadioGroup
    private lateinit var spinnerConfig: Spinner
    private lateinit var textViewNoConfig: TextView
    private lateinit var buttonSave: Button
    private lateinit var buttonCancel: Button

    private var selectedFrpType: FrpType = FrpType.FRPC
    private var configFiles: List<String> = emptyList()
    private var selectedConfigFile: String? = null

    override fun assignFromInput(input: TaskerInput<StartFrpInput>) {
        // Load previously saved configuration
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

    override val inputForTasker: TaskerInput<StartFrpInput>
        get() {
            val frpType = selectedFrpType.typeName
            val configFileName = selectedConfigFile ?: ""
            return TaskerInput(StartFrpInput(frpType, configFileName))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasker_config_selector)

        // Initialize views
        radioGroupType = findViewById(R.id.radioGroupFrpType)
        spinnerConfig = findViewById(R.id.spinnerConfig)
        textViewNoConfig = findViewById(R.id.textViewNoConfig)
        buttonSave = findViewById(R.id.buttonSave)
        buttonCancel = findViewById(R.id.buttonCancel)

        // Load input from Tasker if editing existing action
        taskerHelper.onCreate()

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

        // Load config files
        loadConfigFiles()

        // Set up buttons
        buttonSave.setOnClickListener {
            if (selectedConfigFile != null) {
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
class StartFrpRunner : TaskerPluginRunnerActionNoOutput<StartFrpInput>() {
    override fun run(context: Context, input: TaskerInput<StartFrpInput>): TaskerPluginResult<Unit> {
        // Check if Tasker is allowed
        val preferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
        val allowTasker = preferences.getBoolean(PreferencesKey.ALLOW_TASKER, true)

        if (!allowTasker) {
            return TaskerPluginResultError(
                Exception("Tasker integration is disabled. Please enable it in Settings.")
            )
        }

        val frpTypeStr = input.regular.frpType
        val configFileName = input.regular.configFileName

        if (frpTypeStr.isNullOrBlank() || configFileName.isNullOrBlank()) {
            return TaskerPluginResultError(
                Exception("Invalid configuration: frpType and configFileName must be provided")
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

        // Start the FRP service
        val intent = Intent(context, ShellService::class.java).apply {
            action = ShellServiceAction.START
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
