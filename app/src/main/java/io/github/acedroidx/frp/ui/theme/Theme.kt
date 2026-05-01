package io.github.acedroidx.frp.ui.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun FrpTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    useMonet: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorSchemeMode = when {
        useMonet && themeMode == AppThemeMode.SYSTEM -> ColorSchemeMode.MonetSystem
        useMonet && themeMode == AppThemeMode.LIGHT -> ColorSchemeMode.MonetLight
        useMonet && themeMode == AppThemeMode.DARK -> ColorSchemeMode.MonetDark
        themeMode == AppThemeMode.SYSTEM -> ColorSchemeMode.System
        themeMode == AppThemeMode.LIGHT -> ColorSchemeMode.Light
        themeMode == AppThemeMode.DARK -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }
    val fallbackKeyColor = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            null
        } else {
            Color(0xFF3482FF)
        }
    }
    val controller = remember(colorSchemeMode, fallbackKeyColor) {
        ThemeController(
            colorSchemeMode = colorSchemeMode,
            keyColor = fallbackKeyColor,
        )
    }
    MiuixTheme(controller = controller, content = content)
}
