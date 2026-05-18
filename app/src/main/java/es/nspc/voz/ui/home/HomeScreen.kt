package es.nspc.voz.ui.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.nspc.voz.ServiceLocator
import es.nspc.voz.core.api.HistorialItemDto
import es.nspc.voz.core.telephony.CallState
import es.nspc.voz.core.telephony.RegisterState
import es.nspc.voz.service.foreground.TelephonyForegroundService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val registerState by ServiceLocator.telephony.registerState.collectAsState()
    val callState by ServiceLocator.telephony.callState.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var historial by remember { mutableStateOf<List<HistorialItemDto>>(emptyList()) }

    LaunchedEffect(Unit) {
        TelephonyForegroundService.start(context)
        historial = ServiceLocator.telefoniaApi.historial(50)
    }

    if (callState !is CallState.Idle) {
        ActiveCallScreen(callState)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NSPC Voz") },
                actions = {
                    AvailabilityChip(registerState)
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        coroutineScope.launch {
                            ServiceLocator.auth.signOut()
                            TelephonyForegroundService.stop(context)
                        }
                    }) { Text("Salir") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Recientes") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Marcar") })
            }
            when (tab) {
                0 -> RecientesTab(
                    historial = historial,
                    onRefresh = {
                        coroutineScope.launch { historial = ServiceLocator.telefoniaApi.historial(50) }
                    },
                    onCall = { phone, displayName, clienteId ->
                        coroutineScope.launch {
                            ServiceLocator.telephony.callOut(phone, clienteId, displayName)
                        }
                    },
                )
                1 -> DialerTab(onCall = { phone ->
                    coroutineScope.launch {
                        ServiceLocator.telephony.callOut(phone, null, null)
                    }
                })
            }
        }
    }
}

@Composable
private fun AvailabilityChip(reg: RegisterState) {
    val (color, label) = when (reg) {
        is RegisterState.Registered -> Color(0xFF10B981) to "Disponible"
        is RegisterState.Connecting -> Color(0xFFF59E0B) to "Conectando…"
        is RegisterState.Failed -> Color(0xFFEF4444) to "Sin conexión"
        is RegisterState.Disconnected -> Color(0xFF6B7280) to "Offline"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier
            .size(10.dp)
            .background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RecientesTab(
    historial: List<HistorialItemDto>,
    onRefresh: () -> Unit,
    onCall: (phone: String, displayName: String?, clienteId: String?) -> Unit,
) {
    if (historial.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Sin llamadas todavía", color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onRefresh) { Text("Recargar") }
            }
        }
        return
    }
    LazyColumn(modifier = Modifier
        .fillMaxSize()
        .padding(8.dp)) {
        items(historial, key = { it.id }) { item ->
            HistorialRow(item, onCall)
            HorizontalDivider()
        }
    }
}

@Composable
private fun HistorialRow(
    item: HistorialItemDto,
    onCall: (phone: String, displayName: String?, clienteId: String?) -> Unit,
) {
    val phone = item.callerPhone ?: return
    val displayName = item.cliente?.let { listOfNotNull(it.nombre, it.apellidos).joinToString(" ") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCall(phone, displayName, item.cliente?.id) }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (item.direccion == "entrante") Icons.Default.CallReceived else Icons.Default.CallMade,
            contentDescription = null,
            tint = if (item.estado == "finalizada") Color(0xFF10B981) else Color(0xFFEF4444),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayName ?: phone,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                "${item.createdAt.take(16).replace('T', ' ')} · ${item.estado}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )
        }
        item.duracionSegundos?.let {
            val mins = it / 60
            val secs = it % 60
            Text("${mins}:${secs.toString().padStart(2, '0')}", color = Color.Gray)
        }
    }
}

@Composable
private fun DialerTab(onCall: (phone: String) -> Unit) {
    var phone by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = phone.ifEmpty { "Marca un número" },
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 24.dp),
        )
        DialerGrid(onDigit = { phone += it }, onBackspace = { if (phone.isNotEmpty()) phone = phone.dropLast(1) })
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (phone.isNotBlank()) onCall(normalizeToE164(phone))
            },
            enabled = phone.length >= 4,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
            shape = CircleShape,
            modifier = Modifier.size(72.dp),
        ) {
            Icon(Icons.Default.Call, contentDescription = "Llamar", tint = Color.White)
        }
    }
}

@Composable
private fun DialerGrid(onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("+", "0", "<"),
    )
    rows.forEach { row ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            row.forEach { key ->
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color(0xFFE5E7EB), CircleShape)
                        .clickable {
                            when (key) {
                                "<" -> onBackspace()
                                else -> onDigit(key)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (key == "<") {
                        Icon(Icons.Default.Backspace, contentDescription = "Borrar")
                    } else {
                        Text(key, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveCallScreen(call: CallState) {
    val coroutineScope = rememberCoroutineScope()
    var muted by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val (title, subtitle) = when (call) {
                is CallState.Active -> (call.displayName ?: call.phone) to "En llamada"
                is CallState.Dialing -> (call.displayName ?: call.to) to "Llamando…"
                is CallState.Ringing -> (call.displayName ?: call.from) to "Entrante"
                is CallState.Error -> "Error" to call.message
                CallState.Idle -> "" to ""
            }
            Text(subtitle, color = Color.Gray, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(48.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                if (call is CallState.Ringing) {
                    IconButton(
                        onClick = { coroutineScope.launch { ServiceLocator.telephony.answer() } },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFF10B981), CircleShape),
                    ) { Icon(Icons.Default.Call, contentDescription = "Aceptar", tint = Color.White) }
                }
                IconButton(
                    onClick = {
                        muted = !muted
                        ServiceLocator.telephony.mute(muted)
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .background(if (muted) Color(0xFFEF4444) else Color(0xFF374151), CircleShape),
                ) { Icon(Icons.Default.MicOff, contentDescription = "Mute", tint = Color.White) }
                IconButton(
                    onClick = { coroutineScope.launch { ServiceLocator.telephony.hangup() } },
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color(0xFFEF4444), CircleShape),
                ) { Icon(Icons.Default.CallEnd, contentDescription = "Colgar", tint = Color.White) }
            }
        }
    }
}

private fun normalizeToE164(input: String): String {
    val raw = input.replace(Regex("[^+0-9]"), "")
    return when {
        raw.startsWith("+") -> raw
        raw.startsWith("00") -> "+${raw.substring(2)}"
        raw.matches(Regex("^[6789]\\d{8}$")) -> "+34$raw"
        else -> "+$raw"
    }
}
