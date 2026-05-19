package es.nspc.voz.core.peticiones

import android.util.Log
import es.nspc.voz.core.api.PeticionPendiente
import es.nspc.voz.core.api.TelefoniaApi
import es.nspc.voz.core.auth.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Petición lista para pintar en la UI (con nombre de cliente ya resuelto). */
data class PeticionUi(
    val id: String,
    val callerPhone: String,
    val clienteId: String?,
    val motivo: String?,
    val clienteNombre: String?,
)

/**
 * Vigila la tabla `peticiones_humano` y expone la petición pendiente activa.
 *
 * Dos fuentes que se complementan:
 *  - Supabase Realtime: INSERT estado=pendiente / UPDATE estado=aceptada (instantáneo)
 *  - Polling cada 4s a /api/voice/peticiones-humano/pendientes (respaldo fiable
 *    vía Bearer; cubre la recepción aunque Realtime no autentique o no entregue)
 *
 * Muestra una petición a la vez. IDs ya vistos se ignoran (dedup entre fuentes).
 * Cuando una petición pasa a estado=aceptada, se limpia el estado.
 *
 * Logs con prefijo [peticion-watcher].
 */
class PeticionWatcher(
    private val supabase: SupabaseClient,
    private val auth: AuthRepository,
    private val peticionesApi: PeticionesApi,
    private val telefoniaApi: TelefoniaApi,
) {
    private val _activa = MutableStateFlow<PeticionUi?>(null)
    val activa: StateFlow<PeticionUi?> = _activa.asStateFlow()

    private val vistas = mutableSetOf<String>()
    private val phonesActivas = mutableSetOf<String>()
    private var pollJob: Job? = null
    private var realtimeJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true }

    fun start(scope: CoroutineScope) {
        if (pollJob != null) return
        pollJob = scope.launch { pollLoop() }
        realtimeJob = scope.launch { realtimeLoop(scope) }
    }

    fun stop() {
        pollJob?.cancel(); pollJob = null
        realtimeJob?.cancel(); realtimeJob = null
    }

    /** El gestor aceptó/descartó la petición activa: liberar el estado. */
    fun limpiarActiva() {
        val p = _activa.value
        if (p != null) phonesActivas.remove(p.callerPhone)
        _activa.value = null
    }

    private suspend fun pollLoop() {
        while (true) {
            peticionesApi.getPendientes().forEach { onPendiente(it) }
            delay(4_000L)
        }
    }

    private suspend fun realtimeLoop(scope: CoroutineScope) {
        runCatching {
            auth.ensureRealtimeAuth()
            val channel = supabase.channel("peticiones-humano-app")
            val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "peticiones_humano"
            }
            val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "peticiones_humano"
            }
            channel.subscribe()
            scope.launch {
                inserts.collect { action ->
                    decodeRecord(action)?.let { if (it.estado == "pendiente") onPendiente(it) }
                }
            }
            scope.launch {
                updates.collect { action ->
                    decodeRecord(action)?.let { p ->
                        if (p.estado == "aceptada" && _activa.value?.id == p.id) {
                            Log.d("peticion-watcher", "petición ${p.id} aceptada por otro")
                            limpiarActiva()
                        }
                    }
                }
            }
        }.onFailure {
            Log.w("peticion-watcher", "Realtime no disponible, sigue solo el poller: ${it.message}")
        }
    }

    private fun decodeRecord(action: io.github.jan.supabase.realtime.HasRecord): PeticionPendiente? =
        runCatching {
            json.decodeFromJsonElement(PeticionPendiente.serializer(), action.record)
        }.getOrNull()

    private suspend fun onPendiente(p: PeticionPendiente) {
        if (p.estado != "pendiente") return
        if (p.id in vistas) return
        vistas.add(p.id)
        // Dedup por número: si ya hay una petición viva de este caller, ignorar.
        if (p.callerPhone in phonesActivas) return
        // Solo mostramos una a la vez.
        if (_activa.value != null) return
        phonesActivas.add(p.callerPhone)

        val nombre = runCatching {
            telefoniaApi.resolveByPhone(p.callerPhone)?.let {
                "${it.nombre} ${it.apellidos ?: ""}".trim()
            }
        }.getOrNull()

        _activa.value = PeticionUi(
            id = p.id,
            callerPhone = p.callerPhone,
            clienteId = p.clienteId,
            motivo = p.motivo,
            clienteNombre = nombre?.ifBlank { null },
        )
    }
}
