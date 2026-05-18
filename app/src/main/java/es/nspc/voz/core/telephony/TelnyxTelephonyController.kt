package es.nspc.voz.core.telephony

import android.content.Context
import android.util.Log
import com.telnyx.webrtc.sdk.Call
import com.telnyx.webrtc.sdk.CredentialConfig
import com.telnyx.webrtc.sdk.TelnyxClient
import com.telnyx.webrtc.sdk.model.LogLevel
import com.telnyx.webrtc.sdk.model.SocketMethod
import com.telnyx.webrtc.sdk.model.SocketStatus
import com.telnyx.webrtc.sdk.verto.receive.InviteResponse
import com.telnyx.webrtc.sdk.verto.receive.ReceivedMessageBody
import es.nspc.voz.core.api.TelefoniaApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class TelnyxTelephonyController(
    private val context: Context,
    private val api: TelefoniaApi,
) : TelephonyController {

    private val tag = "TelnyxController"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _registerState = MutableStateFlow<RegisterState>(RegisterState.Disconnected)
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    override val registerState: StateFlow<RegisterState> = _registerState.asStateFlow()
    override val callState: StateFlow<CallState> = _callState.asStateFlow()

    private var client: TelnyxClient? = null
    private var currentLlamadaId: String? = null
    private var iniciadaAt: Long? = null
    private var currentCallId: UUID? = null
    private var pendingInviteCallId: UUID? = null
    private var pendingInviteFrom: String? = null
    private var socketJob: Job? = null

    override suspend fun connectAndRegister(): Result<Unit> = runCatching {
        if (client != null) return@runCatching
        _registerState.value = RegisterState.Connecting
        val cred = api.getSipCredentials()
        val c = TelnyxClient(context)
        client = c
        socketJob?.cancel()
        socketJob = scope.launch { observeSocket(c) }
        c.connect()
        c.credentialLogin(
            CredentialConfig(
                sipUser = cred.sipUsername,
                sipPassword = cred.sipPassword,
                sipCallerIDName = "NSPC Voz",
                sipCallerIDNumber = null,
                fcmToken = null,
                ringtone = null,
                ringBackTone = null,
                logLevel = LogLevel.NONE,
                autoReconnect = true,
            )
        )
    }.onFailure {
        Log.e(tag, "connectAndRegister failed", it)
        _registerState.value = RegisterState.Failed(it.message ?: "unknown")
    }

    private suspend fun observeSocket(c: TelnyxClient) {
        c.socketResponseFlow.collect { response ->
            when (response.status) {
                SocketStatus.ESTABLISHED -> Log.d(tag, "socket established")
                SocketStatus.MESSAGERECEIVED -> handleMessage(response.data as? ReceivedMessageBody)
                SocketStatus.LOADING -> Unit
                SocketStatus.ERROR -> _registerState.value = RegisterState.Failed(response.errorMessage ?: "socket error")
                SocketStatus.DISCONNECT -> {
                    _registerState.value = RegisterState.Disconnected
                    _callState.value = CallState.Idle
                }
            }
        }
    }

    private fun handleMessage(message: ReceivedMessageBody?) {
        if (message == null) return
        when (message.method) {
            SocketMethod.CLIENT_READY.methodName,
            SocketMethod.LOGIN.methodName -> {
                _registerState.value = RegisterState.Registered
            }
            SocketMethod.INVITE.methodName -> {
                val invite = message.result as? InviteResponse ?: return
                pendingInviteCallId = invite.callId
                pendingInviteFrom = invite.callerIdNumber
                scope.launch {
                    val cliente = runCatching { api.resolveByPhone(invite.callerIdNumber ?: "") }.getOrNull()
                    val displayName = cliente?.let { listOfNotNull(it.nombre, it.apellidos).joinToString(" ") }
                    _callState.value = CallState.Ringing(
                        callId = invite.callId.toString(),
                        from = invite.callerIdNumber ?: "?",
                        displayName = displayName,
                    )
                }
            }
            SocketMethod.ANSWER.methodName -> {
                val callId = currentCallId ?: return
                iniciadaAt = System.currentTimeMillis()
                val current = _callState.value
                val phone = when (current) {
                    is CallState.Dialing -> current.to
                    is CallState.Ringing -> current.from
                    else -> "?"
                }
                val name = when (current) {
                    is CallState.Dialing -> current.displayName
                    is CallState.Ringing -> current.displayName
                    else -> null
                }
                _callState.value = CallState.Active(
                    callId = callId.toString(),
                    phone = phone,
                    displayName = name,
                    startedAt = iniciadaAt ?: System.currentTimeMillis(),
                )
            }
            SocketMethod.BYE.methodName -> {
                val llamadaId = currentLlamadaId
                val dur = iniciadaAt?.let { ((System.currentTimeMillis() - it) / 1000).toInt() }
                if (llamadaId != null) {
                    scope.launch {
                        runCatching { api.postFinalizar(llamadaId, dur, "normal") }
                    }
                }
                resetCallState()
            }
            SocketMethod.RINGING.methodName -> Unit
        }
    }

    private fun resetCallState() {
        currentCallId = null
        currentLlamadaId = null
        iniciadaAt = null
        pendingInviteCallId = null
        pendingInviteFrom = null
        _callState.value = CallState.Idle
    }

    override suspend fun callOut(phoneE164: String, clienteId: String?, displayName: String?): Result<Unit> = runCatching {
        val c = client ?: error("WebRTC no registrado")
        if (currentLlamadaId != null) error("Hay una llamada en curso")
        val resp = api.postLlamar(phoneE164, clienteId)
        currentLlamadaId = resp.llamadaId
        val call: Call = c.newInvite(
            callerName = "NSPC Voz",
            callerNumber = "+34950720153",
            destinationNumber = phoneE164,
            clientState = resp.llamadaId,
        )
        currentCallId = call.callId
        _callState.value = CallState.Dialing(
            callId = call.callId.toString(),
            to = phoneE164,
            displayName = displayName,
        )
    }.onFailure {
        Log.e(tag, "callOut failed", it)
        currentLlamadaId?.let { id ->
            scope.launch { runCatching { api.postFinalizar(id, 0, "error") } }
        }
        resetCallState()
        _callState.value = CallState.Error(it.message ?: "Error desconocido")
    }

    override suspend fun answer() {
        val c = client ?: return
        val pendingId = pendingInviteCallId ?: return
        val pendingFrom = pendingInviteFrom ?: return
        runCatching { c.acceptCall(callId = pendingId, destinationNumber = pendingFrom) }
        currentCallId = pendingId
        iniciadaAt = System.currentTimeMillis()
        val current = _callState.value as? CallState.Ringing
        _callState.value = CallState.Active(
            callId = pendingId.toString(),
            phone = pendingFrom,
            displayName = current?.displayName,
            startedAt = iniciadaAt!!,
        )
        pendingInviteCallId = null
        pendingInviteFrom = null
    }

    override suspend fun reject() {
        hangup()
    }

    override suspend fun hangup() {
        val c = client ?: return
        val callId = currentCallId ?: pendingInviteCallId ?: return
        runCatching { c.endCall(callId) }
        val llamadaId = currentLlamadaId
        val dur = iniciadaAt?.let { ((System.currentTimeMillis() - it) / 1000).toInt() } ?: 0
        if (llamadaId != null) {
            scope.launch { runCatching { api.postFinalizar(llamadaId, dur, "normal") } }
        }
        resetCallState()
    }

    override fun mute(on: Boolean) {
        val c = client ?: return
        val callId = currentCallId ?: return
        val call = c.getActiveCalls()[callId] ?: return
        call.onMuteUnmutePressed()
    }

    override suspend fun disconnect() {
        socketJob?.cancel()
        socketJob = null
        runCatching { client?.disconnect() }
        client = null
        _registerState.value = RegisterState.Disconnected
        resetCallState()
    }
}
