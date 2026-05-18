package es.nspc.voz.core.telephony

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.telnyx.webrtc.sdk.CredentialConfig
import com.telnyx.webrtc.sdk.TelnyxClient
import com.telnyx.webrtc.sdk.model.AudioConstraints
import com.telnyx.webrtc.sdk.model.LogLevel
import com.telnyx.webrtc.sdk.model.SocketMethod
import com.telnyx.webrtc.sdk.model.SocketStatus
import com.telnyx.webrtc.sdk.model.TxServerConfiguration
import com.telnyx.webrtc.sdk.stats.CallQuality
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
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _registerState = MutableStateFlow<RegisterState>(RegisterState.Disconnected)
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    private val _transcript = MutableStateFlow<List<TranscriptItem>>(emptyList())
    override val registerState: StateFlow<RegisterState> = _registerState.asStateFlow()
    override val callState: StateFlow<CallState> = _callState.asStateFlow()
    override val transcript: StateFlow<List<TranscriptItem>> = _transcript.asStateFlow()

    private var client: TelnyxClient? = null
    private var currentLlamadaId: String? = null
    private var iniciadaAt: Long? = null
    private var currentCallId: UUID? = null
    private var currentClienteId: String? = null
    private var currentDisplayName: String? = null
    private var pendingInviteCallId: UUID? = null
    private var pendingInviteFrom: String? = null
    private var socketJob: Job? = null
    private var transcriptJob: Job? = null

    override suspend fun connectAndRegister(): Result<Unit> = runCatching {
        if (client != null) return@runCatching
        _registerState.value = RegisterState.Connecting
        val cred = api.getSipCredentials()
        val c = TelnyxClient(context)
        client = c
        socketJob?.cancel()
        socketJob = scope.launch { observeSocket(c) }
        transcriptJob?.cancel()
        transcriptJob = scope.launch { observeTranscript(c) }
        c.connect(
            providedServerConfig = TxServerConfiguration(),
            credentialConfig = CredentialConfig(
                sipUser = cred.sipUsername,
                sipPassword = cred.sipPassword,
                sipCallerIDName = "NSPC Voz",
                sipCallerIDNumber = null,
                fcmToken = null, // v2.1+ pendiente Firebase project real
                ringtone = null,
                ringBackTone = null,
                logLevel = LogLevel.NONE,
                autoReconnect = true,
                reconnectionTimeout = 60_000L,
            ),
            autoLogin = true,
        )
    }.onFailure {
        Log.e(tag, "connectAndRegister failed", it)
        _registerState.value = RegisterState.Failed(it.message ?: "unknown")
    }

    private suspend fun observeSocket(c: TelnyxClient) {
        c.socketResponseFlow.collect { response ->
            when (response.status) {
                SocketStatus.ESTABLISHED -> {
                    if (_registerState.value !is RegisterState.Registered) {
                        _registerState.value = RegisterState.Connecting
                    }
                }
                SocketStatus.MESSAGERECEIVED -> handleMessage(c, response.data as? ReceivedMessageBody)
                SocketStatus.LOADING -> Unit
                SocketStatus.ERROR -> _registerState.value = RegisterState.Failed(response.errorMessage ?: "socket error")
                SocketStatus.DISCONNECT -> {
                    Log.w(tag, "socket DISCONNECT")
                    _registerState.value = RegisterState.Disconnected
                }
            }
        }
    }

    private suspend fun observeTranscript(c: TelnyxClient) {
        runCatching {
            c.transcriptUpdateFlow.collect { items ->
                _transcript.value = items.map {
                    TranscriptItem(
                        role = it.role,
                        content = it.content,
                        timestampMs = System.currentTimeMillis(),
                    )
                }
            }
        }
    }

    private fun handleMessage(c: TelnyxClient, message: ReceivedMessageBody?) {
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
                _transcript.value = emptyList()
                scope.launch {
                    val cliente = runCatching { api.resolveByPhone(invite.callerIdNumber ?: "") }.getOrNull()
                    val displayName = cliente?.let { listOfNotNull(it.nombre, it.apellidos).joinToString(" ") }
                    currentClienteId = cliente?.id
                    currentDisplayName = displayName
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
                _callState.value = CallState.Active(
                    callId = callId.toString(),
                    phone = phone,
                    displayName = currentDisplayName,
                    clienteId = currentClienteId,
                    startedAt = iniciadaAt!!,
                )
                wireCallQualityListener(c, callId)
            }
            SocketMethod.BYE.methodName -> {
                finalizeIfAny("normal")
                resetCallState()
            }
            SocketMethod.RINGING.methodName -> Unit
        }
    }

    private fun wireCallQualityListener(c: TelnyxClient, callId: UUID) {
        val call = c.getActiveCalls()[callId] ?: return
        call.onCallQualityChange = { metrics ->
            val level = when (metrics.quality) {
                CallQuality.EXCELLENT -> QualityLevel.Excelente
                CallQuality.GOOD -> QualityLevel.Buena
                CallQuality.FAIR -> QualityLevel.Regular
                CallQuality.POOR, CallQuality.BAD -> QualityLevel.Mala
                else -> QualityLevel.Regular
            }
            scope.launch {
                val cur = _callState.value
                if (cur is CallState.Active) {
                    _callState.value = cur.copy(
                        quality = QualityMetrics(
                            mos = metrics.mos,
                            jitterMs = (metrics.jitter * 1000).toInt(),
                            rttMs = (metrics.rtt * 1000).toInt(),
                            level = level,
                        ),
                    )
                }
            }
        }
    }

    private fun resetCallState() {
        currentCallId = null
        currentLlamadaId = null
        iniciadaAt = null
        currentClienteId = null
        currentDisplayName = null
        pendingInviteCallId = null
        pendingInviteFrom = null
        _callState.value = CallState.Idle
    }

    private fun finalizeIfAny(reason: String, nota: String? = null) {
        val llamadaId = currentLlamadaId ?: return
        val dur = iniciadaAt?.let { ((System.currentTimeMillis() - it) / 1000).toInt() } ?: 0
        scope.launch {
            runCatching { api.postFinalizar(llamadaId, dur, reason, nota) }
        }
    }

    override suspend fun callOut(phoneE164: String, clienteId: String?, displayName: String?): Result<Unit> = runCatching {
        val c = client ?: error("WebRTC no registrado")
        if (currentLlamadaId != null) error("Hay una llamada en curso")
        val resp = api.postLlamar(phoneE164, clienteId)
        currentLlamadaId = resp.llamadaId
        currentClienteId = clienteId
        currentDisplayName = displayName
        _transcript.value = emptyList()
        val call = c.newInvite(
            callerName = "NSPC Voz",
            callerNumber = "+34950720153",
            destinationNumber = phoneE164,
            clientState = resp.llamadaId,
            audioConstraints = AudioConstraints(
                echoCancellation = true,
                noiseSuppression = true,
                autoGainControl = true,
            ),
        )
        currentCallId = call.callId
        _callState.value = CallState.Dialing(
            callId = call.callId.toString(),
            to = phoneE164,
            displayName = displayName,
        )
        wireCallQualityListener(c, call.callId)
    }.onFailure {
        Log.e(tag, "callOut failed", it)
        finalizeIfAny("error")
        resetCallState()
        _callState.value = CallState.Error(it.message ?: "Error desconocido")
    }

    override suspend fun answer() {
        val c = client ?: return
        val pendingId = pendingInviteCallId ?: return
        val pendingFrom = pendingInviteFrom ?: return
        runCatching {
            c.acceptCall(
                callId = pendingId,
                destinationNumber = pendingFrom,
                audioConstraints = AudioConstraints(
                    echoCancellation = true,
                    noiseSuppression = true,
                    autoGainControl = true,
                ),
            )
        }
        currentCallId = pendingId
        iniciadaAt = System.currentTimeMillis()
        // Si la entrante no tenía llamada_id (no creada por nosotros vía /llamar),
        // creamos una sintética para que el cron de reconciliación la cierre.
        // En v2.1 server-side debería ya existir desde el webhook Telnyx.
        _callState.value = CallState.Active(
            callId = pendingId.toString(),
            phone = pendingFrom,
            displayName = currentDisplayName,
            clienteId = currentClienteId,
            startedAt = iniciadaAt!!,
        )
        pendingInviteCallId = null
        pendingInviteFrom = null
        wireCallQualityListener(c, pendingId)
    }

    override suspend fun reject() {
        hangup()
    }

    override suspend fun hangup() {
        val c = client ?: return
        val callId = currentCallId ?: pendingInviteCallId ?: return
        runCatching { c.endCall(callId) }
        finalizeIfAny("normal")
        resetCallState()
    }

    override fun mute(on: Boolean) {
        val c = client ?: return
        val callId = currentCallId ?: return
        val call = c.getActiveCalls()[callId] ?: return
        call.onMuteUnmutePressed()
        val cur = _callState.value
        if (cur is CallState.Active) _callState.value = cur.copy(muted = on)
    }

    override fun hold(on: Boolean) {
        val c = client ?: return
        val callId = currentCallId ?: return
        val call = c.getActiveCalls()[callId] ?: return
        call.onHoldUnholdPressed(callId)
        val cur = _callState.value
        if (cur is CallState.Active) _callState.value = cur.copy(holding = on)
    }

    override fun speaker(on: Boolean) {
        audioManager?.isSpeakerphoneOn = on
        val cur = _callState.value
        if (cur is CallState.Active) _callState.value = cur.copy(speakerOn = on)
    }

    override fun sendDtmf(digit: String) {
        val c = client ?: return
        val callId = currentCallId ?: return
        val call = c.getActiveCalls()[callId] ?: return
        runCatching { call.dtmf(callId, digit) }
    }

    override suspend fun disconnect() {
        socketJob?.cancel()
        transcriptJob?.cancel()
        socketJob = null
        transcriptJob = null
        runCatching { client?.disconnect() }
        client = null
        _registerState.value = RegisterState.Disconnected
        resetCallState()
    }
}
