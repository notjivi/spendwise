package com.spendwise.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.spendwise.app.security.AppLockManager
import com.spendwise.app.ui.navigation.AppRoot
import com.spendwise.app.ui.navigation.AuthGateViewModel
import com.spendwise.app.ui.theme.SpendWiseTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// FragmentActivity (not just ComponentActivity) because BiometricPrompt is fragment-based.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val gate: AuthGateViewModel by viewModels()
    @Inject lateinit var lock: AppLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen() // must run before super.onCreate
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { gate.isLoading.value }
        enableEdgeToEdge()
        setContent { SpendWiseTheme { AppRoot(gate) } }
    }

    override fun onStart() { super.onStart(); lock.onForegrounded() }
    override fun onStop() { lock.onBackgrounded(); super.onStop() }
}
