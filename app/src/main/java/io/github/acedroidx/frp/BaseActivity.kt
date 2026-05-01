package io.github.acedroidx.frp

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import io.github.acedroidx.frp.ui.theme.AppThemeMode
import io.github.acedroidx.frp.ui.theme.readAppThemeMode
import java.util.Locale

/**
 * 基础 Activity，在 Activity 创建之前根据用户保存的主题偏好设置正确的主题，
 * 避免页面跳转时主题色闪烁。
 */
open class BaseActivity : AppCompatActivity() {

    protected fun applyEdgeToEdge() {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    override fun attachBaseContext(newBase: Context) {
        // 在 attachBaseContext 中应用主题配置，这是最早能设置主题的时机
        val preferences = newBase.getSharedPreferences("data", MODE_PRIVATE)
        val themeMode = preferences.readAppThemeMode()
        val language = preferences.getString(PreferencesKey.APP_LANGUAGE, "system") ?: "system"

        // 设置 AppCompat 的夜间模式，这会影响后续的 Configuration
        AppCompatDelegate.setDefaultNightMode(themeMode.nightMode)

        // 根据主题模式更新 Configuration
        val useDarkTheme = when (themeMode) {
            AppThemeMode.DARK -> true
            AppThemeMode.LIGHT -> false
            AppThemeMode.SYSTEM -> isSystemInDarkTheme(newBase)
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
