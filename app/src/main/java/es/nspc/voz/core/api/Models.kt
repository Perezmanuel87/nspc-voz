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
