package es.nspc.voz

import android.content.Context
import es.nspc.voz.core.api.ApiClient
import es.nspc.voz.core.api.TelefoniaApi
import es.nspc.voz.core.auth.AuthRepository
import es.nspc.voz.core.auth.JwtStore
import es.nspc.voz.core.auth.SupabaseAuthRepository
import es.nspc.voz.core.telephony.TelephonyController
import es.nspc.voz.core.telephony.TelnyxTelephonyController

object ServiceLocator {
    private lateinit var appContext: Context

    val jwtStore: JwtStore by lazy { JwtStore(appContext) }
    val auth: AuthRepository by lazy { SupabaseAuthRepository(jwtStore) }
    val apiClient: ApiClient by lazy { ApiClient(auth) }
    val telefoniaApi: TelefoniaApi by lazy { TelefoniaApi(apiClient) }
    val telephony: TelephonyController by lazy { TelnyxTelephonyController(appContext, telefoniaApi) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
