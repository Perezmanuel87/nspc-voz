package es.nspc.voz.ui.cliente

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.nspc.voz.ServiceLocator
import es.nspc.voz.core.api.ClienteContextoDto
import es.nspc.voz.core.api.HistorialItemDto
import es.nspc.voz.ui.common.PendingCall
import es.nspc.voz.ui.common.RgpdGate
import kotlinx.coroutines.launch

/**
 * Mini-ficha de un cliente. Se abre tocando una llamada de Recientes.
 * Muestra cabecera, pólizas, próximo vencimiento e histórico de llamadas
 * del cliente. Acciones: llamar y enviar SMS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClienteFichaScreen(clienteId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var contexto by remember { mutableStateOf<ClienteContextoDto?>(null) }
    var llamadas by remember { mutableStateOf<List<HistorialItemDto>>(emptyList()) }
    var smsAbierto by remember { mutableStateOf(false) }
    var pendingCall by remember { mutableStateOf<PendingCall?>(null) }

    LaunchedEffect(clienteId) {
        contexto = ServiceLocator.telefoniaApi.getContexto(clienteId)
        llamadas = ServiceLocator.telefoniaApi.historial(limit = 50, clienteId = clienteId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ficha de cliente") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        },
    ) { padding ->
        val ctx = contexto
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (ctx != null) Cabecera(ctx) else Text("Cargando…", color = Color.Gray)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val telefono = ctx?.cliente?.telefono
                    Button(
                        onClick = {
                            if (telefono != null) {
                                pendingCall = PendingCall(telefono, clienteId, ctx?.cliente?.nombre)
                            }
                        },
                        enabled = telefono != null,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Llamar")
                    }
                    OutlinedButton(
                        onClick = { smsAbierto = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Sms, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Enviar SMS")
                    }
                }
            }
            if (ctx != null && ctx.polizas.isNotEmpty()) {
                item {
                    Seccion("Pólizas vigentes") {
                        Text(ctx.polizas.joinToString(", ") { it.ramo }, fontSize = 14.sp)
                    }
                }
            }
            if (ctx != null && ctx.vencimientos.isNotEmpty()) {
                item {
                    Seccion("Próximo vencimiento") {
                        val v = ctx.vencimientos.first()
                        Text("${v.ramo} · ${v.fechaVencimiento}", fontSize = 14.sp, color = Color(0xFFB45309))
                    }
                }
            }
            item { Text("Histórico de llamadas", fontWeight = FontWeight.SemiBold) }
            if (llamadas.isEmpty()) {
                item { Text("Sin llamadas registradas", color = Color.Gray, fontSize = 13.sp) }
            } else {
                items(llamadas, key = { it.id }) { LlamadaRow(it) }
            }
        }
    }

    if (smsAbierto) {
        EnviarSmsSheet(clienteId = clienteId, onDismiss = { smsAbierto = false })
    }

    RgpdGate(
        pending = pendingCall,
        onConfirm = { p ->
            scope.launch {
                ServiceLocator.telephony.callOut(p.phone, p.clienteId, p.displayName)
            }
            pendingCall = null
            onBack()
        },
        onDismiss = { pendingCall = null },
    )
}

@Composable
private fun Cabecera(ctx: ClienteContextoDto) {
    val c = ctx.cliente
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color(0xFF334155), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                c.nombre.take(1) + (c.apellidos?.take(1) ?: ""),
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                listOfNotNull(c.nombre, c.apellidos).joinToString(" "),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            c.telefono?.let { Text(it, color = Color.Gray, fontSize = 14.sp) }
            val sub = listOfNotNull(c.profesion, c.poblacion).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, color = Color.Gray, fontSize = 13.sp)
        }
        if (c.noAsegurable == true) {
            Box(
                modifier = Modifier
                    .background(Color(0xFFEF4444), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("No asegurable", color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun Seccion(titulo: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF1F5F9), RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Text(titulo, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun LlamadaRow(item: HistorialItemDto) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (item.direccion == "entrante") Icons.AutoMirrored.Filled.CallReceived
            else Icons.AutoMirrored.Filled.CallMade,
            contentDescription = null,
            tint = if (item.estado == "finalizada") Color(0xFF10B981) else Color(0xFFEF4444),
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            val fechaHora = item.createdAt.take(16).replace('T', ' ')
            Text(fechaHora, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(item.asunto ?: "Sin asunto", fontSize = 13.sp, color = Color.Gray)
        }
        item.duracionSegundos?.let {
            Text("${it / 60}:${(it % 60).toString().padStart(2, '0')}", color = Color.Gray, fontSize = 13.sp)
        }
    }
}
