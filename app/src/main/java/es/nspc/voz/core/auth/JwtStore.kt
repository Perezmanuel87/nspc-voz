package es.nspc.voz.core.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class JwtStore(context: Context) {
    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "nspc_auth",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(jwt: String, refreshToken: String, userId: String, email: String) {
        prefs.edit()
            .putString("jwt", jwt)
            .putString("refresh", refreshToken)
            .putString("user_id", userId)
            .putString("email", email)
            .apply()
    }

    fun jwt(): String? = prefs.getString("jwt", null)
    fun refreshToken(): String? = prefs.getString("refresh", null)
    fun userId(): String? = prefs.getString("user_id", null)
    fun email(): String? = prefs.getString("email", null)

    fun clear() {
        prefs.edit().clear().apply()
    }
}
