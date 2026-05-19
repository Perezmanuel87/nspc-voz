package es.nspc.voz.ui.call

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.nspc.voz.ServiceLocator
import es.nspc.voz.core.telephony.CallState
import es.nspc.voz.ui.theme.NspcVozTheme
import kotlinx.coroutines.launch

/**
 * Activity con FullScreenIntent que muestra la pantalla de llamada entrante
 * sobre el lockscreen. La dispara la notificación PRIORITY_MAX con
 * setFullScreenIntent() desde el TelephonyForegroundService cuando llega INVITE.
 */
class IncomingCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
        setContent { NspcVozTheme { IncomingScreen(onDone = { finish() }) } }
    }
}

@Composable
fun IncomingScreen(onDone: () -> Unit) {
    val call by ServiceLocator.telephony.callState.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(call) {
        // Si la llamada ya no está en Ringing, cerrar esta pantalla.
        if (call !is CallState.Ringing) onDone()
    }
    val ringing = call as? CallState.Ringing ?: return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text(
                if (ringing.silenced) "Llamada entrante · silenciada" else "Llamada entrante",
                color = if (ringing.silenced) Color(0xFFFBBF24) else Color.Gray,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = ringing.displayName ?: ringing.from,
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (ringing.displayName != null) {
                Spacer(Modifier.height(8.dp))
                Text(ringing.from, color = Color.Gray, fontSize = 18.sp)
            }
            Spacer(Modifier.height(72.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                ActionButton(
                    bg = Color(0xFFEF4444),
                    icon = Icons.Default.CallEnd,
                    label = "Rechazar",
                    onClick = { scope.launch { ServiceLocator.telephony.reject(); onDone() } },
                )
                ActionButton(
                    bg = if (ringing.silenced) Color(0xFF6B7280) else Color(0xFFF59E0B),
                    icon = Icons.Default.NotificationsOff,
                    label = if (ringing.silenced) "Silenciada" else "Silenciar",
                    enabled = !ringing.silenced,
                    onClick = { scope.launch { ServiceLocator.telephony.silence() } },
                )
                ActionButton(
                    bg = Color(0xFF10B981),
                    icon = Icons.Default.Call,
                    label = "Aceptar",
                    onClick = { scope.launch { ServiceLocator.telephony.answer(); onDone() } },
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    bg: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(72.dp)
                .background(bg, CircleShape),
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}
