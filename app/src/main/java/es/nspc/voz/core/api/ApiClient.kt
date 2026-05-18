package es.nspc.voz.core.api

import es.nspc.voz.BuildConfig
import es.nspc.voz.core.auth.AuthRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ApiClient(private val auth: AuthRepository) {
    val http: HttpClient = HttpClient(Android) {
        defaultRequest {
            url(BuildConfig.API_BASE_URL)
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            level = if (BuildConfig.DEBUG) LogLevel.HEADERS else LogLevel.NONE
        }
    }

    suspend fun bearerHeader(): Pair<String, String>? {
        val jwt = auth.currentJwt() ?: return null
        return "Authorization" to "Bearer $jwt"
    }
}

internal suspend fun ApiClient.applyAuth(block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {}): io.ktor.client.request.HttpRequestBuilder {
    return io.ktor.client.request.HttpRequestBuilder().apply {
        block()
        bearerHeader()?.let { (k, v) -> header(k, v) }
    }
}
