package es.nspc.voz.ui.cliente

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.nspc.voz.ServiceLocator
import es.nspc.voz.core.api.ClienteDto
import kotlinx.coroutines.delay

/**
 * Tab "Clientes": buscador por nombre o teléfono que abre la mini-ficha del
 * cliente seleccionado. Apoyo en /api/clientes/search del CRM.
 */
@Composable
fun ClientesScreen(onOpenFicha: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var resultados by remember { mutableStateOf<List<ClienteDto>>(emptyList()) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    // Debounce 250 ms
    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            resultados = emptyList()
            error = null
            return@LaunchedEffect
        }
        delay(250)
        cargando = true
        error = null
        runCatching {
            ServiceLocator.telefoniaApi.searchClientes(query.trim(), limit = 20)
        }.onSuccess { resultados = it }.onFailure {
            error = "Sin conexión"
            resultados = emptyList()
        }
        cargando = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Nombre o teléfono") },
            singleLine = true,
        )

        Spacer(Modifier.size(8.dp))

        when {
            query.trim().length < 2 -> CenterText("Busca por nombre o teléfono")
            cargando -> CenterText("Buscando…")
            error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error!!, color = Color(0xFFEF4444))
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = {
                        // Forzar re-trigger del LaunchedEffect cambiando y restaurando
                        val q = query; query = ""; query = q
                    }) { Text("Reintentar") }
                }
            }
            resultados.isEmpty() -> CenterText("Sin coincidencias para «${query.trim()}»")
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(resultados, key = { it.id }) { cli ->
                    ResultadoRow(cli, onClick = { onOpenFicha(cli.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ResultadoRow(cli: ClienteDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(Color(0xFF334155), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                cli.nombre.take(1) + (cli.apellidos?.take(1) ?: ""),
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                listOfNotNull(cli.nombre, cli.apellidos).joinToString(" "),
                fontWeight = FontWeight.Medium,
            )
            cli.telefono?.let {
                Text(it, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
        cli.valoracion?.let {
            Text("★ $it", color = Color(0xFFF59E0B), fontSize = 13.sp)
        }
    }
}

@Composable
private fun CenterText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Color.Gray)
    }
}
