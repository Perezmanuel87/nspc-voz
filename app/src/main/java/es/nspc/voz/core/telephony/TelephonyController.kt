package es.nspc.voz.core.telephony

import kotlinx.coroutines.flow.StateFlow

sealed class RegisterState {
    data object Disconnected : RegisterState()
    data object Connecting : RegisterState()
    data object Registered : RegisterState()
    data class Failed(val reason: String) : RegisterState()
}

sealed class CallState {
    data object Idle : CallState()
    data class Ringing(val callId: String, val from: String, val displayName: String?) : CallState()
    data class Dialing(val callId: String, val to: String, val displayName: String?) : CallState()
    data class Active(val callId: String, val phone: String, val displayName: String?, val startedAt: Long) : CallState()
    data class Error(val message: String) : CallState()
}

interface TelephonyController {
    val registerState: StateFlow<RegisterState>
    val callState: StateFlow<CallState>

    suspend fun connectAndRegister(): Result<Unit>
    suspend fun disconnect()
    suspend fun callOut(phoneE164: String, clienteId: String?, displayName: String?): Result<Unit>
    suspend fun answer()
    suspend fun reject()
    suspend fun hangup()
    fun mute(on: Boolean)
}
