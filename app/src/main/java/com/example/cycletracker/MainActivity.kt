package com.example.cycletracker

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.example.cycletracker.ui.CycleApp

private val ExpressiveTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.04).em,
            fontFeatureSettings = "tnum"
        ),
        displayMedium = base.displayMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.035).em,
            fontFeatureSettings = "tnum"
        ),
        headlineMedium = base.headlineMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.03).em,
            fontFeatureSettings = "tnum"
        ),
        titleLarge = base.titleLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.025).em
        ),
        titleMedium = base.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).em
        ),
        titleSmall = base.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.015).em
        )
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = LocalContext.current
            val colors = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                darkTheme -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(
                colorScheme = colors,
                typography = ExpressiveTypography
            ) {
                CycleApp()
            }
        }
    }
}
