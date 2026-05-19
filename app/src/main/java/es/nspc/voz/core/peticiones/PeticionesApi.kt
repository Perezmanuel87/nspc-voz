package es.nspc.voz.core.peticiones

import es.nspc.voz.core.api.AceptarResponse
import es.nspc.voz.core.api.ApiClient
import es.nspc.voz.core.api.PeticionPendiente
import es.nspc.voz.core.api.PendientesResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.isSuccess

/**
 * Cliente de los endpoints del flujo "pedir humano" en nspc2.
 * Mismo patrón Ktor + Bearer que [es.nspc.voz.core.api.TelefoniaApi].
 */
class PeticionesApi(private val client: ApiClient) {

    /** Peticiones pendientes de los últimos 30s. Lista vacía si falla. */
    suspend fun getPendientes(): List<PeticionPendiente> = runCatching {
        client.http.get("/api/voice/peticiones-humano/pendientes") {
            client.bearerHeader()?.let { header(it.first, it.second) }
        }.body<PendientesResponse>().peticiones
    }.getOrDefault(emptyList())

    /**
     * Acepta una petición. Devuelve [AceptarResult]:
     *  - Ok con la respuesta del server
     *  - YaAceptada si el server respondió 409 (otro gestor ganó la carrera)
     *  - Error en cualquier otro fallo
     */
    suspend fun aceptar(id: String): AceptarResult {
        return runCatching {
            val res = client.http.post("/api/voice/pide-humano/$id/aceptar") {
                client.bearerHeader()?.let { header(it.first, it.second) }
            }
            when {
                res.status.isSuccess() -> AceptarResult.Ok(res.body())
                res.status.value == 409 -> AceptarResult.YaAceptada
                else -> AceptarResult.Error("HTTP ${res.status.value}")
            }
        }.getOrElse { AceptarResult.Error(it.message ?: "error de red") }
    }
}

sealed class AceptarResult {
    data class Ok(val response: AceptarResponse) : AceptarResult()
    data object YaAceptada : AceptarResult()
    data class Error(val message: String) : AceptarResult()
}
