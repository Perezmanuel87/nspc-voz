package es.nspc.voz.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Wizard de 3 pantallas al primer arranque que explica permisos.
 * Avanza con "Siguiente" hasta que finaliza.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }

    val pages = listOf(
        Page(
            emoji = "📞",
            title = "Llamadas profesionales",
            body = "NSPC Voz sustituye al WebRTC en Chrome con un softphone nativo. Necesitará micrófono y notificaciones para funcionar.",
        ),
        Page(
            emoji = "🔋",
            title = "Optimización de batería",
            body = "Para no perder llamadas entrantes, Android no debe matar la app en background. Te pedimos desactivar la optimización de batería para NSPC Voz.",
            actionLabel = "Abrir ajustes de batería",
            action = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    runCatching {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                }
            },
        ),
        Page(
            emoji = "🔔",
            title = "Notificaciones",
            body = "Cuando entre una llamada, te sonará una notificación de prioridad alta aunque tengas la pantalla bloqueada.",
        ),
    )

    val page = pages[step]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(Modifier.height(48.dp))
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(page.emoji, fontSize = 64.sp)
            Spacer(Modifier.height(16.dp))
            Text(page.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(16.dp))
            Text(page.body, fontSize = 16.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
            if (page.action != null) {
                Spacer(Modifier.height(20.dp))
                TextButton(onClick = page.action) { Text(page.actionLabel ?: "") }
            }
        }
        Column {
            Button(
                onClick = {
                    if (step < pages.lastIndex) step += 1
                    else onFinish()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
            ) {
                Text(if (step == pages.lastIndex) "Empezar" else "Siguiente", color = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Paso ${step + 1} de ${pages.size}",
                modifier = Modifier.fillMaxWidth(),
                color = Color.Gray,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

private data class Page(
    val emoji: String,
    val title: String,
    val body: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
)
