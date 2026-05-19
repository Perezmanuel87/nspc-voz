package es.nspc.voz.core.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClienteDto(
    val id: String,
    val nombre: String,
    val apellidos: String? = null,
    val telefono: String? = null,
    val valoracion: Int? = null,
    @SerialName("tipo_cliente") val tipoCliente: String? = null,
    val email: String? = null,
    @SerialName("fecha_nacimiento") val fechaNacimiento: String? = null,
    val profesion: String? = null,
    val poblacion: String? = null,
    val provincia: String? = null,
    @SerialName("no_asegurable") val noAsegurable: Boolean? = null,
)

@Serializable
data class SipCredentialsDto(
    @SerialName("sip_username") val sipUsername: String,
    @SerialName("sip_password") val sipPassword: String,
)

@Serializable
data class LlamarRequest(val to: String, @SerialName("cliente_id") val clienteId: String? = null)

@Serializable
data class LlamarResponse(
    val ok: Boolean,
    @SerialName("llamada_id") val llamadaId: String,
    val to: String,
)

@Serializable
data class FinalizarRequest(
    @SerialName("duracion_segundos") val duracionSegundos: Int? = null,
    @SerialName("end_reason") val endReason: String,
    val nota: String? = null,
)

@Serializable
data class DispositivoRequest(
    @SerialName("fcm_token") val fcmToken: String,
    val plataforma: String = "android",
    @SerialName("app_version") val appVersion: String,
    @SerialName("modelo_dispositivo") val modeloDispositivo: String? = null,
    @SerialName("recibir_llamadas") val recibirLlamadas: Boolean = true,
)

@Serializable
data class HistorialItemDto(
    val id: String,
    val direccion: String,
    val estado: String,
    @SerialName("caller_phone") val callerPhone: String? = null,
    @SerialName("duracion_segundos") val duracionSegundos: Int? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("ended_at") val endedAt: String? = null,
    val cliente: ClienteDto? = null,
)

@Serializable
data class GestionItemDto(
    val id: String,
    val asunto: String? = null,
    val estado: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class PolizaItemDto(
    val id: String,
    val ramo: String,
    @SerialName("prima_total") val primaTotal: Double? = null,
    @SerialName("fecha_vencimiento") val fechaVencimiento: String? = null,
    val estado: String,
)

@Serializable
data class VencimientoItemDto(
    val id: String,
    val ramo: String,
    @SerialName("prima_orientativa") val primaOrientativa: Double? = null,
    @SerialName("fecha_vencimiento") val fechaVencimiento: String,
    val estado: String,
)

@Serializable
data class ClienteContextoDto(
    val cliente: ClienteDto,
    val gestiones: List<GestionItemDto> = emptyList(),
    val polizas: List<PolizaItemDto> = emptyList(),
    val vencimientos: List<VencimientoItemDto> = emptyList(),
)

@Serializable
data class HeartbeatRequest(
    @SerialName("register_state") val registerState: String,
)

@Serializable
data class PeticionPendiente(
    val id: String,
    @SerialName("caller_phone") val callerPhone: String,
    @SerialName("cliente_id") val clienteId: String? = null,
    val motivo: String? = null,
    val estado: String,
    @SerialName("aceptada_por") val aceptadaPor: String? = null,
)

@Serializable
data class PendientesResponse(
    val peticiones: List<PeticionPendiente> = emptyList(),
)

@Serializable
data class AutoCall(
    val numero: String,
    @SerialName("cliente_id") val clienteId: String? = null,
)

@Serializable
data class AceptarResponse(
    val ok: Boolean = false,
    @SerialName("gestion_id") val gestionId: String? = null,
    val transferido: Boolean = false,
    @SerialName("auto_call") val autoCall: AutoCall? = null,
)
