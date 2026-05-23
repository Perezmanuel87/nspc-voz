package es.nspc.voz.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Modal que recuerda al gestor leer el aviso RGPD de grabación antes de
 * marcar. Mismo copy que el modal del CRM web
 * (src/components/telefonia/boton-llamar.tsx).
 */
@Composable
fun RgpdDisclaimerDialog(
    onAccept: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Recordatorio RGPD") },
        text = {
            Text(
                "Antes de hablar, di al cliente:\n\n" +
                    "«Le aviso de que esta llamada queda grabada por motivos de calidad y trazabilidad.»",
            )
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
            ) {
                Text("Llamar")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancelar") }
        },
    )
}
