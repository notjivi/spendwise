package com.spendwise.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.spendwise.app.data.local.Category

private val Light = lightColorScheme(primary = Color(0xFF00695C), secondary = Color(0xFF4F6F52))
private val Dark = darkColorScheme(primary = Color(0xFF4DB6AC), secondary = Color(0xFFA8C3A9))

@Composable
fun SpendWiseTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = when {
        // Material You colors from the wallpaper on Android 12+; static palette below that.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        darkTheme -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Fixed per-category colors so a category looks the same on every chart. */
fun categoryColor(c: Category): Color = when (c) {
    Category.FOOD -> Color(0xFFEF6C00)
    Category.TRANSPORT -> Color(0xFF1E88E5)
    Category.SHOPPING -> Color(0xFF8E24AA)
    Category.BILLS -> Color(0xFF546E7A)
    Category.ENTERTAINMENT -> Color(0xFFD81B60)
    Category.HEALTH -> Color(0xFF43A047)
    Category.EDUCATION -> Color(0xFF3949AB)
    Category.TRAVEL -> Color(0xFF00ACC1)
    Category.OTHER -> Color(0xFF8D6E63)
}
