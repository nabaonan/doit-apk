package com.quandian.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * 字体：全局改用系统无衬线（圆润、视觉和谐）——华为真机上为 HarmonyOS Sans，
 * 其余设备为 Noto Sans / Roboto。不额外打包字体文件，保持 APK 轻量。
 */
val SansFamily = FontFamily.SansSerif

/** 标题 */
val displayStyle = TextStyle(
    fontFamily = SansFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 34.sp,
    letterSpacing = 0.sp,
)

/** 小标题：面板 / 页面标题 */
val titleStyle = TextStyle(
    fontFamily = SansFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 17.sp,
    letterSpacing = 0.2.sp,
)

/** 数字 / 拉丁 */
fun numeric(size: Int, weight: FontWeight = FontWeight.SemiBold) = TextStyle(
    fontFamily = SansFamily,
    fontWeight = weight,
    fontSize = size.sp,
    letterSpacing = (-0.2).sp,
)

val Typography = Typography(
    bodyLarge = TextStyle(fontFamily = SansFamily, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = SansFamily, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = SansFamily, fontSize = 11.sp, lineHeight = 16.sp),
    titleLarge = displayStyle,
    titleMedium = titleStyle,
    labelSmall = TextStyle(
        fontFamily = SansFamily,
        fontSize = 10.sp,
        letterSpacing = 1.6.sp,
        fontWeight = FontWeight.SemiBold,
    ),
)

/** 主题模式：跟随系统 / 强制浅色 / 强制深色 */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        fun from(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** 按设置与系统状态算出最终是不是深色 */
fun resolveDark(mode: String?, systemDark: Boolean): Boolean = when (ThemeMode.from(mode)) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Material 色板从 QD 令牌映射而来。
 * 写成函数：每次组合都从 QD 取值，QD.use(...) 换调色板时这里随之刷新。
 */
private fun lightScheme() = lightColorScheme(
    primary = QD.Accent,
    onPrimary = QD.OnAccent,
    primaryContainer = QD.AccentSoft,
    onPrimaryContainer = QD.AccentInk,
    background = QD.Screen,
    onBackground = QD.Ink,
    surface = QD.Surface,
    onSurface = QD.Ink,
    surfaceVariant = QD.Surface2,
    onSurfaceVariant = QD.InkSoft,
    outline = QD.Line,
    error = QD.Danger,
    scrim = QD.Scrim,
)

private fun darkScheme() = darkColorScheme(
    primary = QD.Accent,
    onPrimary = QD.OnAccent,
    primaryContainer = QD.AccentSoft,
    onPrimaryContainer = QD.AccentInk,
    background = QD.Screen,
    onBackground = QD.Ink,
    surface = QD.Surface,
    onSurface = QD.Ink,
    surfaceVariant = QD.Surface2,
    onSurfaceVariant = QD.InkSoft,
    outline = QD.Line,
    error = QD.Danger,
    scrim = QD.Scrim,
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 全局主题入口。
 *
 * `dark` 由 [resolveDark] 算好传进来（设置页的「主题」三项 + 系统状态）。
 * 这里做三件事：
 * 1. 把对应调色板装进 [QD]（写在 material 与内容之前，同一帧内生效，不会闪一帧旧色）
 * 2. Material 色板按当前调色板重建，深色用 darkColorScheme
 * 3. 状态栏 / 导航栏图标跟着反色，深色下变成浅色图标
 */
@Composable
fun QuandianTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (dark) QDPalette.Midnight else QDPalette.Mint
    if (QD.palette !== palette) QD.use(palette)

    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    LaunchedEffect(dark, activity, view) {
        val window = activity?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalContentColor provides QD.Ink) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme() else lightScheme(),
            typography = Typography,
            content = content,
        )
    }
}
