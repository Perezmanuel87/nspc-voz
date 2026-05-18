package es.nspc.voz.core.telephony

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed class RegisterState {
    data object Disconnected : RegisterState()
    data object Connecting : RegisterState()
    data object Registered : RegisterState()
    data class Failed(val reason: String) : RegisterState()
}

enum class QualityLevel { Excelente, Buena, Regular, Mala }

data class QualityMetrics(
    val mos: Double,
    val jitterMs: Int,
    val rttMs: Int,
    val level: QualityLevel,
)

sealed class CallState {
    data object Idle : CallState()
    data class Ringing(
        val callId: String,
        val from: String,
        val displayName: String?,
    ) : CallState()
    data class Dialing(
        val callId: String,
        val to: String,
        val displayName: String?,
    ) : CallState()
    data class Active(
        val callId: String,
        val phone: String,
        val displayName: String?,
        val clienteId: String?,
        val startedAt: Long,
        val holding: Boolean = false,
        val muted: Boolean = false,
        val speakerOn: Boolean = false,
        val quality: QualityMetrics? = null,
    ) : CallState()
    data class Reconnecting(val callId: String) : CallState()
    data class Error(val message: String) : CallState()
}

interface TelephonyController {
    val registerState: StateFlow<RegisterState>
    val callState: StateFlow<CallState>
    val transcript: StateFlow<List<TranscriptItem>>

    suspend fun connectAndRegister(): Result<Unit>
    suspend fun disconnect()
    suspend fun callOut(phoneE164: String, clienteId: String?, displayName: String?): Result<Unit>
    suspend fun answer()
    suspend fun reject()
    suspend fun hangup()
    fun mute(on: Boolean)
    fun hold(on: Boolean)
    fun speaker(on: Boolean)
    fun sendDtmf(digit: String)
}

data class TranscriptItem(
    val role: String, // "user" | "assistant" | "agent" | "caller"
    val content: String,
    val timestampMs: Long,
)
