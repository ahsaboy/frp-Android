package io.github.acedroidx.frp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.acedroidx.frp.ui.theme.FrpTheme
import kotlinx.coroutines.flow.MutableStateFlow

class BatteryOptimizationGuideActivity : BaseActivity() {
    private val batteryOptimizationWhitelisted = MutableStateFlow(false)
    private val themeMode = MutableStateFlow("跟随系统")

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferences = getSharedPreferences("data", MODE_PRIVATE)
        themeMode.value = preferences.getString(PreferencesKey.THEME_MODE, "跟随系统") ?: "跟随系统"
        refreshBatteryOptimizationStatus()

        enableEdgeToEdge()
        setContent {
            val currentTheme by themeMode.collectAsStateWithLifecycle("跟随系统")
            FrpTheme(themeMode = currentTheme) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.battery_optimization_guide_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_arrow_back_24dp),
                                        contentDescription = stringResource(R.string.dismiss)
                                    )
                                }
                            }
                        )
                    }
                ) { contentPadding ->
                    GuideContent(
                        modifier = Modifier.padding(contentPadding),
                        isWhitelisted = batteryOptimizationWhitelisted.collectAsStateWithLifecycle(false).value
                    )
                }
            }
        }
    }

    @Composable
    private fun GuideContent(modifier: Modifier = Modifier, isWhitelisted: Boolean) {
        val statusText = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M ->
                stringResource(R.string.battery_optimization_not_applicable)
            isWhitelisted ->
                stringResource(R.string.battery_optimization_whitelisted)
            else ->
                stringResource(R.string.battery_optimization_not_whitelisted)
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.battery_optimization_guide_desc),
                style = MaterialTheme.typography.bodyMedium
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isWhitelisted) {
                Button(
                    onClick = { requestIgnoreBatteryOptimization() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.battery_optimization_request_button))
                }
            }
            Button(
                onClick = { openBatteryOptimizationSettings() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.battery_optimization_open_settings_button))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshBatteryOptimizationStatus()
    }

    private fun refreshBatteryOptimizationStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            batteryOptimizationWhitelisted.value = true
            return
        }

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        batteryOptimizationWhitelisted.value =
            powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (_: Exception) {
            openBatteryOptimizationSettings()
        }
    }

    private fun openBatteryOptimizationSettings() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Unable to open settings", Toast.LENGTH_SHORT).show()
        }
    }
}
