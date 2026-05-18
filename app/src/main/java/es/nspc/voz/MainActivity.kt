package es.nspc.voz

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import es.nspc.voz.core.auth.AuthState
import es.nspc.voz.ui.home.HomeScreen
import es.nspc.voz.ui.login.LoginScreen
import es.nspc.voz.ui.onboarding.OnboardingScreen
import es.nspc.voz.ui.settings.SettingsScreen
import es.nspc.voz.ui.theme.NspcVozTheme

class MainActivity : ComponentActivity() {

    private val permsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* sin handling extra v1 */ }

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("nspc_app", MODE_PRIVATE)
        requestRuntimePermissions()
        setContent {
            NspcVozTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val authState by ServiceLocator.auth.state.collectAsState()
                    var route by remember { mutableStateOf<Route>(Route.Auto) }

                    val needsOnboarding = !prefs.getBoolean("onboarding_done", false)

                    when {
                        authState !is AuthState.Authenticated -> LoginScreen()
                        needsOnboarding && route is Route.Auto -> OnboardingScreen(onFinish = {
                            prefs.edit().putBoolean("onboarding_done", true).apply()
                            route = Route.Home
                        })
                        route is Route.Settings -> SettingsScreen(onBack = { route = Route.Home })
                        else -> HomeScreen(onOpenSettings = { route = Route.Settings })
                    }
                }
            }
        }
    }

    private fun requestRuntimePermissions() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed += Manifest.permission.BLUETOOTH_CONNECT
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permsLauncher.launch(missing.toTypedArray())
        }
    }
}

private sealed interface Route {
    data object Auto : Route
    data object Home : Route
    data object Settings : Route
}
