package es.nspc.voz.ui.peticion

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.nspc.voz.ServiceLocator
import es.nspc.voz.core.api.AutoCall
import es.nspc.voz.core.peticiones.AceptarResult
import kotlinx.coroutines.launch

/**
 * Pantalla completa (sobre el lockscreen) que muestra una petición "pedir
 * humano". La dispara la notificación PRIORITY_MAX del TelephonyForegroundService.
 *
 * Estados:
 *  - Sonando: nombre / motivo / teléfono + Descartar / Yo le atiendo
 *  - Aceptando: spinner textual
 *  - Aceptada: botón grande "Llamar a +34..."
 *  - Cerrar: finish()
 */
class IncomingPetitionActivity : ComponentActivity() {
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
        setContent { PetitionScreen(onDone = { finish() }) }
    }
}

private sealed class UiState {
    data object Sonando : UiState()
    data object Aceptando : UiState()
    data class Aceptada(val autoCall: AutoCall?) : UiState()
    data class Mensaje(val texto: String) : UiState()
}

@Composable
private fun PetitionScreen(onDone: () -> Unit) {
    val watcher = ServiceLocator.peticionWatcher
    val peticion by watcher.activa.collectAsState()
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf<UiState>(UiState.Sonando) }

    // Mientras está "Sonando", si el watcher pierde la petición (otro gestor
    // la aceptó, o expiró) cerrar la pantalla. Tras aceptar, el estado local
    // manda y ya no dependemos del watcher.
    LaunchedEffect(peticion, ui) {
        if (ui is UiState.Sonando && peticion == null) onDone()
    }

    val p = peticion
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp).fillMaxWidth(),
        ) {
            Text("Un cliente pide un gestor", color = Color(0xFFF59E0B), fontSize = 16.sp)
            Spacer(Modifier.height(20.dp))
            Text(
                text = p?.clienteNombre ?: p?.callerPhone ?: "Llamada anónima",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (p?.clienteNombre != null) {
                Spacer(Modifier.height(6.dp))
                Text(p.callerPhone, color = Color.Gray, fontSize = 16.sp)
            }
            if (!p?.motivo.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(p!!.motivo!!, color = Color(0xFFCBD5E1), fontSize = 16.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(48.dp))

            when (val s = ui) {
                is UiState.Sonando -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(
                        onClick = { watcher.limpiarActiva(); onDone() },
                        modifier = Modifier.height(56.dp),
                    ) { Text("Descartar") }
                    Button(
                        onClick = {
                            val id = p?.id ?: return@Button
                            ui = UiState.Aceptando
                            scope.launch {
                                when (val r = ServiceLocator.peticionesApi.aceptar(id)) {
                                    is AceptarResult.Ok -> {
                                        ui = UiState.Aceptada(r.response.autoCall)
                                        watcher.limpiarActiva()
                                    }
                                    is AceptarResult.YaAceptada -> {
                                        ui = UiState.Mensaje("La coge un compañero")
                                        watcher.limpiarActiva()
                                    }
                                    is AceptarResult.Error -> ui = UiState.Mensaje("Error: ${r.message}")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(56.dp),
                    ) { Text("Yo le atiendo", fontWeight = FontWeight.Bold) }
                }

                is UiState.Aceptando -> Text("Aceptando…", color = Color.Gray, fontSize = 16.sp)

                is UiState.Aceptada -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✓ Aceptada · gestión asignada a ti", color = Color(0xFF10B981), fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Espera unos segundos a que Delia cuelgue, luego llama:",
                        color = Color(0xFFCBD5E1), fontSize = 13.sp, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    val ac = s.autoCall
                    Button(
                        onClick = {
                            if (ac == null) { onDone(); return@Button }
                            scope.launch {
                                ServiceLocator.telephony.callOut(
                                    phoneE164 = ac.numero,
                                    clienteId = ac.clienteId,
                                    displayName = p?.clienteNombre,
                                )
                                onDone()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2410C)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                    ) {
                        Text(
                            if (ac != null) "Llamar a ${ac.numero}" else "Cerrar",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }

                is UiState.Mensaje -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.texto, color = Color(0xFFCBD5E1), fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onDone, modifier = Modifier.height(52.dp)) { Text("Cerrar") }
                }
            }
        }
    }
}
