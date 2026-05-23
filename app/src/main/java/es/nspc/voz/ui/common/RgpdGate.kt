package es.nspc.voz.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Datos de una llamada pendiente, que esperan a que se pase el gate RGPD.
 */
data class PendingCall(
    val phone: String,
    val clienteId: String?,
    val displayName: String?,
)

/**
 * Renderizar este Composable en cada pantalla que dispare llamadas.
 * Patrón: el call site mantiene un `var pending by remember { mutableStateOf<PendingCall?>(null) }`.
 * Al pulsar Llamar, en lugar de invocar callOut, hace `pending = PendingCall(...)`.
 *
 * - Si RgpdSession.aceptado=true → onConfirm(pending) inmediato.
 * - Si false → muestra el dialog; aceptar marca sesión + onConfirm; cancelar → onDismiss.
 */
@Composable
fun RgpdGate(
    pending: PendingCall?,
    onConfirm: (PendingCall) -> Unit,
    onDismiss: () -> Unit,
) {
    if (pending == null) return
    if (RgpdSession.aceptado) {
        LaunchedEffect(pending) { onConfirm(pending) }
        return
    }
    RgpdDisclaimerDialog(
        onAccept = {
            RgpdSession.aceptado = true
            onConfirm(pending)
        },
        onCancel = onDismiss,
    )
}
