package com.hamza.todo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hamza.todo.core.model.Priority
import com.hamza.todo.data.ThemeMode

/** Colors the design uses beyond the Material scheme. */
@Immutable
data class TodoColors(
    val card: Color,
    val muted: Color,
    val line: Color,
    val danger: Color,
    val dangerContainer: Color,
    val success: Color,
    val successContainer: Color,
    val track: Color,
    val dark: Boolean,
) {
    fun priority(p: Priority): Color = when (p) {
        Priority.HIGH -> if (dark) Color(0xFFFF9C66) else Color(0xFFC2410C)
        Priority.MEDIUM -> if (dark) Color(0xFFF2C14E) else Color(0xFF9A5B00)
        Priority.LOW -> if (dark) Color(0xFF8FB0FF) else Color(0xFF2F62D6)
        Priority.NONE -> if (dark) Color(0xFF5F6672) else Color(0xFF9AA1AD)
    }

    fun priorityContainer(p: Priority): Color = when (p) {
        Priority.HIGH -> if (dark) Color(0xFF4A2414) else Color(0xFFFDEBDD)
        Priority.MEDIUM -> if (dark) Color(0xFF43340F) else Color(0xFFFBEFD6)
        Priority.LOW -> if (dark) Color(0xFF1C2B4F) else Color(0xFFE1E9FB)
        Priority.NONE -> if (dark) Color(0xFF262A31) else Color(0xFFE9EBEF)
    }

    /** List colors are stored for light mode; lighten them on dark backgrounds. */
    fun listColor(argb: Long): Color {
        val c = Color(argb)
        return if (!dark) c else Color(
            red = c.red + (1 - c.red) * 0.35f,
            green = c.green + (1 - c.green) * 0.35f,
            blue = c.blue + (1 - c.blue) * 0.35f,
        )
    }
}

private val LightTodo = TodoColors(
    card = Color.White,
    muted = Color(0xFF566070),
    line = Color(0xFFE2E5EA),
    danger = Color(0xFFB42318),
    dangerContainer = Color(0xFFFDECEA),
    success = Color(0xFF0F766E),
    successContainer = Color(0xFFDDF2EF),
    track = Color(0xFFC9CFF5),
    dark = false,
)

private val DarkTodo = TodoColors(
    card = Color(0xFF1A1D23),
    muted = Color(0xFFA6ACB8),
    line = Color(0xFF2A2E36),
    danger = Color(0xFFFF8F85),
    dangerContainer = Color(0xFF3B1D1B),
    success = Color(0xFF4FD1C5),
    successContainer = Color(0xFF173532),
    track = Color(0xFF3A4170),
    dark = true,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3346D3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E8FB),
    onPrimaryContainer = Color(0xFF2535A8),
    secondaryContainer = Color(0xFFE5E8FB),
    onSecondaryContainer = Color(0xFF2535A8),
    background = Color(0xFFF4F5F8),
    onBackground = Color(0xFF14161B),
    surface = Color(0xFFF4F5F8),
    onSurface = Color(0xFF14161B),
    surfaceVariant = Color(0xFFE9EBEF),
    onSurfaceVariant = Color(0xFF566070),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFE9EBEF),
    outline = Color(0xFFC5CBD5),
    outlineVariant = Color(0xFFE2E5EA),
    error = Color(0xFFB42318),
    inverseSurface = Color(0xFF23262D),
    inverseOnSurface = Color(0xFFF1F2F5),
    inversePrimary = Color(0xFFAEB8FF),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9AA6FF),
    onPrimary = Color(0xFF0F1115),
    primaryContainer = Color(0xFF272D55),
    onPrimaryContainer = Color(0xFFD9DDFF),
    secondaryContainer = Color(0xFF272D55),
    onSecondaryContainer = Color(0xFFD9DDFF),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFECEEF2),
    surface = Color(0xFF0F1115),
    onSurface = Color(0xFFECEEF2),
    surfaceVariant = Color(0xFF23272F),
    onSurfaceVariant = Color(0xFFA6ACB8),
    surfaceContainerLowest = Color(0xFF1A1D23),
    surfaceContainerLow = Color(0xFF1A1D23),
    surfaceContainer = Color(0xFF1A1D23),
    surfaceContainerHigh = Color(0xFF1F232A),
    surfaceContainerHighest = Color(0xFF23272F),
    outline = Color(0xFF5F6672),
    outlineVariant = Color(0xFF2A2E36),
    error = Color(0xFFFF8F85),
    inverseSurface = Color(0xFFECEEF2),
    inverseOnSurface = Color(0xFF14161B),
    inversePrimary = Color(0xFF3346D3),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = TextStyle(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
    )
}

val LocalTodoColors = staticCompositionLocalOf { LightTodo }

object Todo {
    val colors: TodoColors
        @Composable get() = LocalTodoColors.current
}

@Composable
fun TodoTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalTodoColors provides if (dark) DarkTodo else LightTodo) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
