package es.nspc.voz.core.auth

import es.nspc.voz.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class AuthState {
    data object Anonymous : AuthState()
    data class Authenticated(val userId: String, val email: String, val jwt: String) : AuthState()
}

interface AuthRepository {
    val state: StateFlow<AuthState>
    suspend fun signIn(email: String, password: String): Result<AuthState.Authenticated>
    suspend fun signOut()
    suspend fun currentJwt(): String?
}

class SupabaseAuthRepository(private val store: JwtStore) : AuthRepository {

    private val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth) {
            alwaysAutoRefresh = true
            autoLoadFromStorage = false
        }
    }

    private val _state = MutableStateFlow<AuthState>(restoreFromStore())
    override val state: StateFlow<AuthState> = _state.asStateFlow()

    private fun restoreFromStore(): AuthState {
        val jwt = store.jwt() ?: return AuthState.Anonymous
        val uid = store.userId() ?: return AuthState.Anonymous
        val email = store.email() ?: return AuthState.Anonymous
        return AuthState.Authenticated(uid, email, jwt)
    }

    override suspend fun signIn(email: String, password: String): Result<AuthState.Authenticated> =
        runCatching {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val session = client.auth.currentSessionOrNull()
                ?: error("Sesión vacía tras signIn")
            val user = session.user ?: error("Usuario vacío en sesión")
            store.save(session.accessToken, session.refreshToken, user.id, email)
            val auth = AuthState.Authenticated(user.id, email, session.accessToken)
            _state.value = auth
            auth
        }

    override suspend fun signOut() {
        runCatching { client.auth.signOut() }
        store.clear()
        _state.value = AuthState.Anonymous
    }

    override suspend fun currentJwt(): String? {
        return runCatching {
            val session = client.auth.currentSessionOrNull()
            if (session != null) {
                val nowSecs = System.currentTimeMillis() / 1000
                val expiresAt = session.expiresAt?.epochSeconds ?: 0
                if (expiresAt - nowSecs < 60) {
                    client.auth.refreshCurrentSession()
                }
                return@runCatching client.auth.currentAccessTokenOrNull()
            }

            val storedJwt = store.jwt() ?: return@runCatching null
            if (!isJwtNearExpiry(storedJwt)) return@runCatching storedJwt

            val refresh = store.refreshToken() ?: return@runCatching null
            val refreshed = client.auth.refreshSession(refresh)
            val newJwt = refreshed.accessToken
            store.save(
                newJwt,
                refreshed.refreshToken,
                store.userId() ?: "",
                store.email() ?: "",
            )
            _state.value = AuthState.Authenticated(
                store.userId() ?: "",
                store.email() ?: "",
                newJwt,
            )
            newJwt
        }.getOrElse { store.jwt() }
    }

    private fun isJwtNearExpiry(jwt: String, marginSecs: Long = 60): Boolean {
        return runCatching {
            val parts = jwt.split(".")
            if (parts.size < 2) return@runCatching true
            val payload = android.util.Base64.decode(
                parts[1],
                android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
            ).toString(Charsets.UTF_8)
            val exp = Regex("\"exp\"\\s*:\\s*(\\d+)")
                .find(payload)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            val nowSecs = System.currentTimeMillis() / 1000
            (exp - nowSecs) < marginSecs
        }.getOrElse { true }
    }
}
