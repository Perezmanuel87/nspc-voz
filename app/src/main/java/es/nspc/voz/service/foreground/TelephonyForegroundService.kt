package es.nspc.voz.service.foreground

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import es.nspc.voz.MainActivity
import es.nspc.voz.R
import es.nspc.voz.ServiceLocator
import es.nspc.voz.core.network.NetType
import es.nspc.voz.core.network.NetworkChangeWatcher
import es.nspc.voz.core.telephony.CallState
import es.nspc.voz.core.telephony.RegisterState
import es.nspc.voz.ui.call.IncomingCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Foreground service que mantiene el TelnyxClient registrado en SIP:
 *
 *  - **Auto-retry exponential backoff** (1s, 2s, 4s, 8s, max 30s) si la
 *    registración cae después de haber estado registrada.
 *  - **NetworkCallback** detecta WiFi↔4G y fuerza reconnect limpio en cuanto
 *    hay red de nuevo.
 *  - **Heartbeat** cada 60s pinguea /api/app/heartbeat con el register_state
 *    para que el cron health-monitor detecte gestores offline.
 *  - **Notificación persistente** refleja estado: Disponible/Conectando/En llamada.
 *  - **Notificación de entrante** con FullScreenIntent + acciones Aceptar/
 *    Silenciar/Rechazar disparadas vía PendingIntent al propio service.
 */
class TelephonyForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null
    private var networkJob: Job? = null
    private var heartbeatJob: Job? = null
    private var retryJob: Job? = null
    private var hasBeenRegistered = false
    private var retryAttempt = 0
    private var lastKnownNet: NetType = NetType.NONE
    private var lastIncomingSilenced: Boolean? = null

    companion object {
        private const val CHANNEL_ID = "telephony_persistent"
        private const val CHANNEL_INCOMING_ID = "telephony_incoming"
        private const val CHANNEL_INCOMING_SILENT_ID = "telephony_incoming_silent"
        private const val NOTIF_ID = 1001
        private const val NOTIF_INCOMING_ID = 1002

        const val ACTION_ANSWER = "es.nspc.voz.action.ANSWER"
        const val ACTION_REJECT = "es.nspc.voz.action.REJECT"
        const val ACTION_SILENCE = "es.nspc.voz.action.SILENCE"

        fun start(context: Context) {
            val intent = Intent(context, TelephonyForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TelephonyForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(NOTIF_ID, buildPersistentNotification("NSPC Voz", "Conectando…"))
        scope.launch { ServiceLocator.telephony.connectAndRegister() }
        observeJob = scope.launch {
            combine(
                ServiceLocator.telephony.registerState,
                ServiceLocator.telephony.callState,
            ) { reg, call -> Pair(reg, call) }
                .collect { (reg, call) ->
                    updatePersistentNotification(reg, call)
                    updateIncomingNotification(call)
                    scheduleRetryIfNeeded(reg)
                }
        }
        networkJob = scope.launch {
            NetworkChangeWatcher(this@TelephonyForegroundService).observe().collect { net ->
                val changed = net != lastKnownNet
                lastKnownNet = net
                if (changed && net != NetType.NONE && hasBeenRegistered) {
                    // Forzar reconexión limpia al cambiar de red
                    retryJob?.cancel()
                    retryJob = scope.launch {
                        ServiceLocator.telephony.disconnect()
                        ServiceLocator.telephony.connectAndRegister()
                        retryJob = null
                    }
                }
            }
        }
        heartbeatJob = scope.launch {
            while (isActive) {
                val state = when (ServiceLocator.telephony.registerState.value) {
                    is RegisterState.Registered -> "registered"
                    is RegisterState.Connecting -> "connecting"
                    is RegisterState.Failed -> "failed"
                    is RegisterState.Disconnected -> "disconnected"
                }
                ServiceLocator.telefoniaApi.heartbeat(state)
                delay(60_000L)
            }
        }
    }

    private fun scheduleRetryIfNeeded(reg: RegisterState) {
        when (reg) {
            is RegisterState.Registered -> {
                hasBeenRegistered = true
                retryAttempt = 0
                retryJob?.cancel()
                retryJob = null
            }
            is RegisterState.Disconnected, is RegisterState.Failed -> {
                if (hasBeenRegistered && retryJob == null) {
                    val delayMs = min(30_000L, 1_000L * (1L shl min(retryAttempt, 5)))
                    retryAttempt += 1
                    retryJob = scope.launch {
                        delay(delayMs)
                        ServiceLocator.telephony.disconnect()
                        ServiceLocator.telephony.connectAndRegister()
                        retryJob = null
                    }
                }
            }
            else -> Unit
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ANSWER -> {
                scope.launch {
                    ServiceLocator.telephony.answer()
                    // Abrir la app para que aparezca la pantalla de llamada activa.
                    startActivity(
                        Intent(this@TelephonyForegroundService, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    )
                }
            }
            ACTION_REJECT -> scope.launch { ServiceLocator.telephony.reject() }
            ACTION_SILENCE -> scope.launch { ServiceLocator.telephony.silence() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observeJob?.cancel()
        networkJob?.cancel()
        heartbeatJob?.cancel()
        retryJob?.cancel()
        scope.launch { ServiceLocator.telephony.disconnect() }
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Telefonía", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Mantiene el softphone conectado"
                setShowBadge(false)
            },
        )
        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING_ID, "Llamadas entrantes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifica con sonido y full-screen las llamadas entrantes"
                setSound(ringtoneUri, audioAttrs)
                enableVibration(true)
                setBypassDnd(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING_SILENT_ID, "Llamadas entrantes (silenciadas)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Entrante silenciada por el usuario, sin sonido ni vibración"
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun updatePersistentNotification(reg: RegisterState, call: CallState) {
        val text = when {
            call is CallState.Active -> "En llamada · ${call.displayName ?: call.phone}"
            call is CallState.Dialing -> "Llamando · ${call.displayName ?: call.to}"
            call is CallState.Ringing -> "Entrante · ${call.displayName ?: call.from}"
            reg is RegisterState.Registered -> "Disponible"
            reg is RegisterState.Connecting -> "Conectando…"
            reg is RegisterState.Failed -> "Sin conexión · ${reg.reason}"
            else -> "Sin conexión"
        }
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIF_ID, buildPersistentNotification("NSPC Voz", text))
    }

    private fun updateIncomingNotification(call: CallState) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (call !is CallState.Ringing) {
            nm.cancel(NOTIF_INCOMING_ID)
            lastIncomingSilenced = null
            return
        }
        // Si cambió el flag silenced, cancelar y republicar para que Android detenga el
        // ringtone y use el canal correcto (los updates con el mismo ID no cortan el sonido).
        if (lastIncomingSilenced != null && lastIncomingSilenced != call.silenced) {
            nm.cancel(NOTIF_INCOMING_ID)
        }
        lastIncomingSilenced = call.silenced
        nm.notify(NOTIF_INCOMING_ID, buildIncomingNotification(call))
    }

    private fun buildPersistentNotification(title: String, text: String): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun buildIncomingNotification(ringing: CallState.Ringing): android.app.Notification {
        val channel = if (ringing.silenced) CHANNEL_INCOMING_SILENT_ID else CHANNEL_INCOMING_ID
        val title = "Llamada entrante"
        val text = ringing.displayName ?: ringing.from

        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
        )
        val fullScreenPi = PendingIntent.getActivity(
            this, 100, fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val answerPi = servicePendingIntent(101, ACTION_ANSWER)
        val rejectPi = servicePendingIntent(102, ACTION_REJECT)
        val silencePi = servicePendingIntent(103, ACTION_SILENCE)

        val builder = NotificationCompat.Builder(this, channel)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(fullScreenPi)
            .addAction(R.drawable.ic_launcher_foreground, "Rechazar", rejectPi)
            .addAction(R.drawable.ic_launcher_foreground, if (ringing.silenced) "Silenciado" else "Silenciar", silencePi)
            .addAction(R.drawable.ic_launcher_foreground, "Aceptar", answerPi)
        if (ringing.silenced) {
            builder.setSilent(true)
        }
        return builder.build()
    }

    private fun servicePendingIntent(requestCode: Int, action: String): PendingIntent {
        val intent = Intent(this, TelephonyForegroundService::class.java).setAction(action)
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
