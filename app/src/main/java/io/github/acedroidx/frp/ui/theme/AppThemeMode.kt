package io.github.acedroidx.frp.ui.theme

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import io.github.acedroidx.frp.PreferencesKey
import io.github.acedroidx.frp.R

enum class AppThemeMode(
    val preferenceValue: String,
    @param:StringRes val labelRes: Int,
    val nightMode: Int,
) {
    SYSTEM(
        preferenceValue = "system",
        labelRes = R.string.theme_follow_system,
        nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
    ),
    LIGHT(
        preferenceValue = "light",
        labelRes = R.string.theme_light,
        nightMode = AppCompatDelegate.MODE_NIGHT_NO,
    ),
    DARK(
        preferenceValue = "dark",
        labelRes = R.string.theme_dark,
        nightMode = AppCompatDelegate.MODE_NIGHT_YES,
    ),
    ;

    companion object {
        fun fromPreferenceValue(rawValue: String?): AppThemeMode = when (rawValue) {
            "light", "static_light", "monet_light", "浅色", "Light" -> LIGHT
            "dark", "static_dark", "monet_dark", "深色", "Dark" -> DARK
            "system", "static_system", "monet_system", "跟随系统", "Follow System",
            "MIUI风格", "MIUI Style", null -> SYSTEM
            else -> SYSTEM
        }
    }
}

fun SharedPreferences.readAppThemeMode(): AppThemeMode =
    AppThemeMode.fromPreferenceValue(getString(PreferencesKey.THEME_MODE, null))

fun SharedPreferences.Editor.putAppThemeMode(mode: AppThemeMode): SharedPreferences.Editor =
    putString(PreferencesKey.THEME_MODE, mode.preferenceValue)

fun SharedPreferences.readUseMonet(): Boolean {
    if (contains(PreferencesKey.THEME_USE_MONET)) {
        return getBoolean(PreferencesKey.THEME_USE_MONET, false)
    }
    // Migrate from old 6-value theme_mode
    val old = getString(PreferencesKey.THEME_MODE, null)
    return old?.startsWith("monet_") == true
}

fun SharedPreferences.Editor.putUseMonet(value: Boolean): SharedPreferences.Editor =
    putBoolean(PreferencesKey.THEME_USE_MONET, value)
