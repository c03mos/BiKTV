package com.hu3h.biktv.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun BiKTVTheme(
    isInDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isInDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (isInDarkTheme) {
            darkColorScheme(
                primary = Slate500,
                secondary = Steel500,
                tertiary = Slate400,
                background = Ink900,
                surface = Ink800,
                onPrimary = Sand50,
                onSecondary = Sand50,
                onBackground = Sand50,
                onSurface = Sand100
            )
        } else {
            lightColorScheme(
                primary = Steel600,
                secondary = Slate500,
                tertiary = Slate400,
                background = Sand50,
                surface = Sand100,
                onPrimary = Sand50,
                onSecondary = Ink900,
                onBackground = Ink900,
                onSurface = Ink800
            )
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
