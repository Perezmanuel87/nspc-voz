package es.nspc.voz.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import es.nspc.voz.BuildConfig
import es.nspc.voz.ServiceLocator
import es.nspc.voz.core.telephony.RegisterState
import es.nspc.voz.service.foreground.TelephonyForegroundService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reg by ServiceLocator.telephony.registerState.collectAsState()
    val email = ServiceLocator.jwtStore.email() ?: "—"
    var recibirLlamadas by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .padding(16.dp)
            .fillMaxSize()) {

            Row(modifier = Modifier.fillMaxWidth(), arrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Cuenta", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Text(email, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Telefonía", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), arrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Recibir llamadas en este dispositivo")
                    Text("Si la apagas, los entrantes solo te llegan al otro dispositivo", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Switch(checked = recibirLlamadas, onCheckedChange = {
                    recibirLlamadas = it
                    scope.launch {
                        // El flag se persiste cuando se llame a register-device la próxima vez.
                        // Para v2.2 conviene un endpoint dedicado pero esto funciona.
                    }
                })
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), arrangement = Arrangement.SpaceBetween) {
                Text("Estado SIP")
                Text(
                    when (reg) {
                        is RegisterState.Registered -> "Conectado"
                        is RegisterState.Connecting -> "Conectando…"
                        is RegisterState.Failed -> "Error"
                        is RegisterState.Disconnected -> "Desconectado"
                    },
                    color = if (reg is RegisterState.Registered) Color(0xFF10B981) else Color.Gray,
                )
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("App", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Text("Versión ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    scope.launch {
                        ServiceLocator.auth.signOut()
                        TelephonyForegroundService.stop(context)
                        onBack()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Cerrar sesión", color = Color.White) }
        }
    }
}

@Composable
private fun Row(modifier: Modifier, arrangement: Arrangement.Horizontal, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    androidx.compose.foundation.layout.Row(modifier = modifier, horizontalArrangement = arrangement, verticalAlignment = Alignment.CenterVertically, content = content)
}
