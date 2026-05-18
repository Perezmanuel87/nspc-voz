package es.nspc.voz.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import es.nspc.voz.core.api.ClienteContextoDto
import es.nspc.voz.core.telephony.CallState
import es.nspc.voz.core.telephony.QualityLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ActiveCallScreen(state: CallState) {
    val coroutineScope = rememberCoroutineScope()
    val transcript by ServiceLocator.telephony.transcript.collectAsState()

    val active = state as? CallState.Active
    val dialing = state as? CallState.Dialing
    val title = active?.displayName ?: active?.phone ?: dialing?.displayName ?: dialing?.to ?: "Llamada"
    val subtitle = if (active != null) "En llamada" else "Llamando…"

    var elapsedSec by remember { mutableStateOf(0) }
    LaunchedEffect(active) {
        if (active != null) {
            while (true) {
                elapsedSec = ((System.currentTimeMillis() - active.startedAt) / 1000).toInt()
                delay(1_000)
            }
        }
    }

    var dialpadOpen by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var contexto by remember { mutableStateOf<ClienteContextoDto?>(null) }

    LaunchedEffect(active?.clienteId) {
        val cid = active?.clienteId
        contexto = if (cid != null) ServiceLocator.telefoniaApi.getContexto(cid) else null
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF0F172A))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(subtitle, color = Color.Gray, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                if (active != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(fmt(elapsedSec), color = Color(0xFF94A3B8), fontSize = 16.sp)
                }
                active?.quality?.let { q -> QualityChip(q.level, q.mos) }
            }

            Spacer(Modifier.height(16.dp))

            // Ficha cliente compacta
            contexto?.let { ctx ->
                ClienteContextCard(ctx)
                Spacer(Modifier.height(12.dp))
            }

            // Transcripción en vivo
            if (transcript.isNotEmpty()) {
                Box(modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                    .padding(12.dp)) {
                    LazyColumn {
                        items(transcript) { item ->
                            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    "${if (item.role == "user" || item.role == "caller") "👤" else "🎧"} ",
                                    fontSize = 14.sp,
                                )
                                Text(item.content, color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            } else {
                Spacer(Modifier.weight(1f))
            }

            // Notas in-call
            if (active != null) {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas para la gestión", color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                )
                Spacer(Modifier.height(12.dp))
            }

            // Botones control
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (active != null) {
                    CallControl(
                        active = active.muted,
                        labelOff = "Mute",
                        labelOn = "Mute",
                    ) {
                        ServiceLocator.telephony.mute(!active.muted)
                    }
                    CallControl(
                        active = active.holding,
                        labelOff = "Pausa",
                        labelOn = "Reanudar",
                        icon = Icons.Default.Pause,
                    ) {
                        ServiceLocator.telephony.hold(!active.holding)
                    }
                    CallControl(
                        active = active.speakerOn,
                        labelOff = "Altavoz",
                        labelOn = "Altavoz",
                        icon = Icons.Default.VolumeUp,
                    ) {
                        ServiceLocator.telephony.speaker(!active.speakerOn)
                    }
                    CallControl(
                        active = dialpadOpen,
                        labelOff = "Teclado",
                        labelOn = "Teclado",
                        icon = Icons.Default.Dialpad,
                    ) {
                        dialpadOpen = !dialpadOpen
                    }
                }
            }

            if (dialpadOpen && active != null) {
                Spacer(Modifier.height(16.dp))
                DtmfPad { digit -> ServiceLocator.telephony.sendDtmf(digit) }
            }

            Spacer(Modifier.height(20.dp))
            // Botón colgar grande centrado
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            // TODO: enviar `notes` al endpoint finalizar (server-side soporta `nota`)
                            ServiceLocator.telephony.hangup()
                        }
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color(0xFFEF4444), CircleShape),
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "Colgar", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CallControl(
    active: Boolean,
    labelOff: String,
    labelOn: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.MicOff,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .background(if (active) Color(0xFF10B981) else Color(0xFF374151), CircleShape),
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Text(if (active) labelOn else labelOff, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
private fun DtmfPad(onDigit: (String) -> Unit) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#"),
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFF334155), CircleShape)
                            .clickable { onDigit(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(key, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityChip(level: QualityLevel, mos: Double) {
    val (color, label) = when (level) {
        QualityLevel.Excelente -> Color(0xFF10B981) to "📶 Excelente"
        QualityLevel.Buena -> Color(0xFF22C55E) to "📶 Buena"
        QualityLevel.Regular -> Color(0xFFF59E0B) to "📶 Regular"
        QualityLevel.Mala -> Color(0xFFEF4444) to "📶 Mala"
    }
    Spacer(Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text("$label · MOS ${"%.1f".format(mos)}", color = color, fontSize = 12.sp)
    }
}

@Composable
private fun ClienteContextCard(ctx: ClienteContextoDto) {
    val c = ctx.cliente
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF334155), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    c.nombre.take(1) + (c.apellidos?.take(1) ?: ""),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.size(8.dp))
            Column {
                Text(listOfNotNull(c.nombre, c.apellidos).joinToString(" "), color = Color.White, fontWeight = FontWeight.SemiBold)
                if (c.profesion != null || c.poblacion != null) {
                    Text(listOfNotNull(c.profesion, c.poblacion).joinToString(" · "), color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
        if (ctx.polizas.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Pólizas vigentes: ${ctx.polizas.joinToString(", ") { it.ramo }}", color = Color(0xFFA3E635), fontSize = 12.sp)
        }
        if (ctx.vencimientos.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("Próximo vence: ${ctx.vencimientos.first().ramo} · ${ctx.vencimientos.first().fechaVencimiento}", color = Color(0xFFFBBF24), fontSize = 12.sp)
        }
        if (ctx.gestiones.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("Última gestión: ${ctx.gestiones.first().asunto ?: ctx.gestiones.first().estado}", color = Color(0xFF93C5FD), fontSize = 12.sp)
        }
    }
}

private fun fmt(secs: Int): String {
    val m = secs / 60
    val s = secs % 60
    return "%d:%02d".format(m, s)
}
