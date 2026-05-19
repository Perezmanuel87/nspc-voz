package es.nspc.voz.core.sms

import es.nspc.voz.core.api.ApiClient
import es.nspc.voz.core.api.SmsPlantillaDto
import es.nspc.voz.core.api.SmsPlantillasResponse
import es.nspc.voz.core.api.SmsPreviewResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/** Cliente de los endpoints SMS de nspc2. Patrón Ktor + Bearer. */
class SmsApi(private val client: ApiClient) {

    /** Lista de plantillas SMS del tenant. Lista vacía si falla. */
    suspend fun getPlantillas(): List<SmsPlantillaDto> = runCatching {
        client.http.get("/api/sms/plantillas") {
            client.bearerHeader()?.let { header(it.first, it.second) }
        }.body<SmsPlantillasResponse>().plantillas
    }.getOrDefault(emptyList())

    /** Texto de una plantilla resuelto server-side para un cliente. */
    suspend fun preview(plantillaId: String, clienteId: String): SmsPreviewResponse? = runCatching {
        client.http.post("/api/sms/plantillas/$plantillaId/preview") {
            client.bearerHeader()?.let { header(it.first, it.second) }
            contentType(ContentType.Application.Json)
            setBody(PreviewRequest(clienteId))
        }.body<SmsPreviewResponse>()
    }.getOrNull()

    /**
     * Envía un SMS al cliente. [SmsResult.Error] lleva el mensaje del server
     * (opt-out, SMS no configurado, teléfono inválido).
     */
    suspend fun enviar(clienteId: String, text: String): SmsResult {
        return runCatching {
            val res = client.http.post("/api/sms/send") {
                client.bearerHeader()?.let { header(it.first, it.second) }
                contentType(ContentType.Application.Json)
                setBody(EnviarRequest(clienteId, text))
            }
            if (res.status.isSuccess()) {
                SmsResult.Ok
            } else {
                val err = runCatching { res.body<ErrorBody>().error }.getOrNull()
                SmsResult.Error(err ?: "Error ${res.status.value}")
            }
        }.getOrElse { SmsResult.Error(it.message ?: "error de red") }
    }

    @Serializable
    private data class PreviewRequest(val cliente_id: String)

    @Serializable
    private data class EnviarRequest(val cliente_id: String, val text: String)

    @Serializable
    private data class ErrorBody(val error: String? = null)
}

sealed class SmsResult {
    data object Ok : SmsResult()
    data class Error(val message: String) : SmsResult()
}
