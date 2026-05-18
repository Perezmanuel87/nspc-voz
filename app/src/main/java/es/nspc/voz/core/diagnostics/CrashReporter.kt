package es.nspc.voz.core.diagnostics

import android.content.Context
import android.os.Build
import es.nspc.voz.BuildConfig
import es.nspc.voz.ServiceLocator
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Reemplazo de Crashlytics sin Firebase. Hookea el
 * `Thread.setDefaultUncaughtExceptionHandler` y manda cada crash a
 * /api/app/crash-log en nspc2. También permite reportar errores no fatales.
 */
object CrashReporter {

    @Serializable
    private data class CrashPayload(
        val fatal: Boolean,
        val message: String,
        val stacktrace: String,
        @SerialName("app_version") val appVersion: String,
        val modelo: String,
        @SerialName("android_version") val androidVersion: String,
    )

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                postSync(fatal = true, throwable = e)
            } catch (_: Throwable) { /* nunca propagar errores del reporter */ }
            previous?.uncaughtException(t, e)
        }
    }

    fun reportNonFatal(message: String, throwable: Throwable? = null) {
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            runCatching { post(fatal = false, message = message, throwable = throwable) }
        }
    }

    private fun postSync(fatal: Boolean, throwable: Throwable) {
        // Para fatales corremos en el mismo hilo (estamos a punto de morir).
        // Es best-effort; si la red está caída el crash se pierde.
        kotlinx.coroutines.runBlocking {
            runCatching {
                post(fatal = fatal, message = throwable.message ?: throwable::class.simpleName ?: "unknown", throwable = throwable)
            }
        }
    }

    private suspend fun post(fatal: Boolean, message: String, throwable: Throwable?) {
        val sw = StringWriter()
        throwable?.printStackTrace(PrintWriter(sw))
        val payload = CrashPayload(
            fatal = fatal,
            message = message.take(500),
            stacktrace = sw.toString().take(8_000),
            appVersion = BuildConfig.VERSION_NAME,
            modelo = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        )
        val http = ServiceLocator.apiClient.http
        val auth = ServiceLocator.apiClient.bearerHeader() ?: return
        http.post("/api/app/crash-log") {
            header(auth.first, auth.second)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }
}
