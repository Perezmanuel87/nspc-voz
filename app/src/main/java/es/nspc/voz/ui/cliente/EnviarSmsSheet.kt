package es.nspc.voz.ui.cliente

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import es.nspc.voz.ServiceLocator
import es.nspc.voz.core.api.SmsPlantillaDto
import es.nspc.voz.core.sms.SmsResult
import kotlinx.coroutines.launch

/**
 * Bottom sheet para enviar un SMS al cliente [clienteId].
 * Carga plantillas; al elegir una, pide el texto resuelto al server y lo
 * vuelca en un campo editable. También permite escribir libre.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EnviarSmsSheet(
    clienteId: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var plantillas by remember { mutableStateOf<List<SmsPlantillaDto>>(emptyList()) }
    var texto by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        plantillas = ServiceLocator.smsApi.getPlantillas()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Enviar SMS")

            if (plantillas.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    plantillas.forEach { p ->
                        AssistChip(
                            onClick = {
                                scope.launch {
                                    val prev = ServiceLocator.smsApi.preview(p.id, clienteId)
                                    if (prev != null) texto = prev.texto
                                    else Toast.makeText(context, "No se pudo cargar la plantilla", Toast.LENGTH_SHORT).show()
                                }
                            },
                            label = { Text(p.nombre) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                label = { Text("Mensaje") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
            )

            Button(
                onClick = {
                    enviando = true
                    scope.launch {
                        when (val r = ServiceLocator.smsApi.enviar(clienteId, texto.trim())) {
                            is SmsResult.Ok -> {
                                Toast.makeText(context, "SMS enviado", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                            is SmsResult.Error -> {
                                Toast.makeText(context, r.message, Toast.LENGTH_LONG).show()
                                enviando = false
                            }
                        }
                    }
                },
                enabled = texto.isNotBlank() && !enviando,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (enviando) "Enviando…" else "Enviar")
            }
        }
    }
}
