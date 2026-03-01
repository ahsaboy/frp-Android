package io.github.acedroidx.frp

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

/**
 * 基础 Activity，在 Activity 创建之前根据用户保存的主题偏好设置正确的主题，
 * 避免页面跳转时主题色闪烁。
 */
open class BaseActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        // 在 attachBaseContext 中应用主题配置，这是最早能设置主题的时机
        val preferences = newBase.getSharedPreferences("data", MODE_PRIVATE)
        val themeMode = preferences.getString(PreferencesKey.THEME_MODE, "跟随系统") ?: "跟随系统"
        val language = preferences.getString(PreferencesKey.APP_LANGUAGE, "system") ?: "system"

        // 设置 AppCompat 的夜间模式，这会影响后续的 Configuration
        val nightMode = when (themeMode) {
            "深色" -> AppCompatDelegate.MODE_NIGHT_YES
            "浅色" -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)

        // 根据主题模式更新 Configuration
        val useDarkTheme = when (themeMode) {
            "深色" -> true
            "浅色" -> false
            else -> isSystemInDarkTheme(newBase)
        }

        val config = Configuration(newBase.resources.configuration)
        applyLanguage(config, newBase, language)
        config.uiMode = if (useDarkTheme) {
            (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        } else {
            (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        }

        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    private fun isSystemInDarkTheme(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun applyLanguage(config: Configuration, context: Context, language: String) {
        val locale = when (language) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "en" -> Locale.ENGLISH
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.resources.configuration.locales[0]
                } else {
                    @Suppress("DEPRECATION")
                    context.resources.configuration.locale
                }
            }
        }
        Locale.setDefault(locale)
        config.setLocale(locale)
    }
}
