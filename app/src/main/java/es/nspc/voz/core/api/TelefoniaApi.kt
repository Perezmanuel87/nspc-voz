package es.nspc.voz.core.api

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class TelefoniaApi(private val client: ApiClient) {

    suspend fun getSipCredentials(): SipCredentialsDto =
        client.http.get("/api/telefonia/sip-credentials") {
            client.bearerHeader()?.let { header(it.first, it.second) }
        }.body()

    suspend fun postLlamar(to: String, clienteId: String?): LlamarResponse =
        client.http.post("/api/telefonia/llamar") {
            client.bearerHeader()?.let { header(it.first, it.second) }
            contentType(ContentType.Application.Json)
            setBody(LlamarRequest(to, clienteId))
        }.body()

    suspend fun postFinalizar(llamadaId: String, duracion: Int?, endReason: String, nota: String? = null) {
        client.http.post("/api/telefonia/llamadas/$llamadaId/finalizar") {
            client.bearerHeader()?.let { header(it.first, it.second) }
            contentType(ContentType.Application.Json)
            setBody(FinalizarRequest(duracion, endReason, nota))
        }
    }

    suspend fun setDisponible(disponible: Boolean) {
        client.http.post("/api/telefonia/disponibilidad") {
            client.bearerHeader()?.let { header(it.first, it.second) }
            contentType(ContentType.Application.Json)
            setBody(mapOf("disponible" to disponible))
        }
    }

    suspend fun resolveByPhone(phone: String): ClienteDto? = runCatching {
        client.http.get("/api/clientes/by-phone") {
            client.bearerHeader()?.let { header(it.first, it.second) }
            parameter("phone", phone)
        }.body<ClienteDto?>()
    }.getOrNull()

    suspend fun searchClientes(q: String, limit: Int = 20): List<ClienteDto> = runCatching {
        client.http.get("/api/clientes/search") {
            client.bearerHeader()?.let { header(it.first, it.second) }
            parameter("q", q)
            parameter("limit", limit)
        }.body<List<ClienteDto>>()
    }.getOrDefault(emptyList())

    suspend fun historial(limit: Int = 50, clienteId: String? = null): List<HistorialItemDto> = runCatching {
        client.http.get("/api/telefonia/historial") {
            client.bearerHeader()?.let { header(it.first, it.second) }
            parameter("limit", limit)
            if (clienteId != null) parameter("cliente_id", clienteId)
        }.body<List<HistorialItemDto>>()
    }.getOrDefault(emptyList())

    suspend fun getContexto(clienteId: String): ClienteContextoDto? = runCatching {
        client.http.get("/api/clientes/$clienteId/contexto") {
            client.bearerHeader()?.let { header(it.first, it.second) }
        }.body<ClienteContextoDto>()
    }.getOrNull()

    suspend fun heartbeat(registerState: String) {
        runCatching {
            client.http.post("/api/app/heartbeat") {
                client.bearerHeader()?.let { header(it.first, it.second) }
                contentType(ContentType.Application.Json)
                setBody(HeartbeatRequest(registerState))
            }
        }
    }

    suspend fun registerDevice(req: DispositivoRequest) {
        runCatching {
            client.http.post("/api/app/register-device") {
                client.bearerHeader()?.let { header(it.first, it.second) }
                contentType(ContentType.Application.Json)
                setBody(req)
            }
        }
    }
}
