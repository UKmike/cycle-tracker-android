package com.example.cycletracker

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.cycletracker.data.CycleRepository
import com.example.cycletracker.ui.CycleApp
import com.example.cycletracker.ui.MayaLogo

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

class MainActivity : FragmentActivity() {

    private var appLockEnabled by mutableStateOf(false)
    private var isLocked by mutableStateOf(false)
    private var promptInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        val lockEnabled = CycleRepository(this).getSettings().appLockEnabled
        appLockEnabled = lockEnabled
        applySecureWindowFlag(lockEnabled)
        isLocked = savedInstanceState?.getBoolean(KEY_IS_LOCKED, lockEnabled) ?: lockEnabled

        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = LocalContext.current
            val colors = remember(darkTheme) {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    }
                    darkTheme -> darkColorScheme()
                    else -> lightColorScheme()
                }
            }
            MaterialTheme(
                colorScheme = colors,
                typography = ExpressiveTypography
            ) {
                if (isLocked && appLockEnabled) {
                    LaunchedEffect(Unit) {
                        launchBiometricUnlock()
                    }
                    AppLockScreen(onUnlockClick = { launchBiometricUnlock() })
                } else {
                    CycleApp(
                        onAppLockSettingChanged = { enabled ->
                            appLockEnabled = enabled
                            applySecureWindowFlag(enabled)
                            if (!enabled) isLocked = false
                        }
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_IS_LOCKED, isLocked)
    }

    override fun onStop() {
        super.onStop()
        // Re-lock only when backgrounded, not on orientation/configuration changes
        if (appLockEnabled && !isChangingConfigurations) {
            isLocked = true
        }
    }

    private fun applySecureWindowFlag(secure: Boolean) {
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun launchBiometricUnlock() {
        if (promptInFlight) return
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val status = BiometricManager.from(this).canAuthenticate(authenticators)
        if (status != BiometricManager.BIOMETRIC_SUCCESS) {
            // If user removed device screen lock from system settings, don't trap them out
            isLocked = false
            return
        }

        promptInFlight = true
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptInFlight = false
                    isLocked = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptInFlight = false
                }

                override fun onAuthenticationFailed() {
                    // System prompt stays open and allows retry
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Maya")
            .setSubtitle("Verify your identity to view your cycle data")
            .setAllowedAuthenticators(authenticators)
            .build()

        prompt.authenticate(promptInfo)
    }

    companion object {
        private const val KEY_IS_LOCKED = "maya_is_locked"
    }
}

@Composable
private fun AppLockScreen(onUnlockClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            MayaLogo(modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Maya is locked",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Authenticate to view your cycle calendar",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onUnlockClick) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Unlock", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
