package es.nspc.voz.service.foreground

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import es.nspc.voz.MainActivity
import es.nspc.voz.R
import es.nspc.voz.ServiceLocator
import es.nspc.voz.core.telephony.CallState
import es.nspc.voz.core.telephony.RegisterState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Mantiene el TelnyxClient registrado en SIP mientras el servicio esté vivo.
 * Sin push (FCM) en v1: este servicio always-on es la única vía para recibir
 * llamadas. Coste: ~3-5% batería/hora, pero garantiza estabilidad.
 */
class TelephonyForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null
    private var retryJob: Job? = null
    private var hasBeenRegistered = false

    companion object {
        private const val CHANNEL_ID = "telephony_persistent"
        private const val NOTIF_ID = 1001

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
        createChannel()
        startForeground(NOTIF_ID, buildNotification("NSPC Voz", "Conectando…"))
        scope.launch {
            ServiceLocator.telephony.connectAndRegister()
        }
        observeJob = scope.launch {
            combine(
                ServiceLocator.telephony.registerState,
                ServiceLocator.telephony.callState,
            ) { reg, call -> Pair(reg, call) }
                .collect { (reg, call) ->
                    updateNotification(reg, call)
                    scheduleRetryIfNeeded(reg)
                }
        }
    }

    private fun scheduleRetryIfNeeded(reg: RegisterState) {
        when (reg) {
            is RegisterState.Registered -> {
                hasBeenRegistered = true
                retryJob?.cancel()
                retryJob = null
            }
            is RegisterState.Disconnected, is RegisterState.Failed -> {
                // Solo reintentar si ya habíamos estado registrados (no en arranque inicial,
                // que ya lo dispara onCreate). Evita loops si las creds son malas.
                if (hasBeenRegistered && retryJob == null) {
                    retryJob = scope.launch {
                        delay(5_000)
                        // disconnect() limpia client=null, sin esto el
                        // connectAndRegister() tiene early-return y no hace nada.
                        ServiceLocator.telephony.disconnect()
                        ServiceLocator.telephony.connectAndRegister()
                        retryJob = null
                    }
                }
            }
            else -> Unit
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observeJob?.cancel()
        retryJob?.cancel()
        scope.launch { ServiceLocator.telephony.disconnect() }
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Telefonía",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Mantiene el softphone conectado"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(reg: RegisterState, call: CallState) {
        val title = "NSPC Voz"
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
        nm.notify(NOTIF_ID, buildNotification(title, text))
    }

    private fun buildNotification(title: String, text: String): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            intent,
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
}
