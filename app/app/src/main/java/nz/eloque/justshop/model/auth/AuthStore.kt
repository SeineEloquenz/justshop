package nz.eloque.justshop.model.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nz.eloque.justshop.Preferences

enum class AuthMode { NONE, BASIC, OIDC }

data class PendingLogin(
    val state: String,
    val nonce: String,
    val codeVerifier: String,
    val serverUrl: String,
    val listName: String?,
)

/**
 * Connection settings and credentials. OIDC secrets live in a separate preference file
 * that is excluded from backups.
 */
@Singleton
class AuthStore @Inject constructor(
    @ApplicationContext context: Context,
    private val prefs: SharedPreferences,
) {
    private val secrets = context.getSharedPreferences(SECRETS_FILE, Context.MODE_PRIVATE)

    private val _onboarded = MutableStateFlow(false)
    val onboarded: StateFlow<Boolean> = _onboarded.asStateFlow()

    private val _signInRequired = MutableStateFlow(false)
    val signInRequired: StateFlow<Boolean> = _signInRequired.asStateFlow()

    init {
        if (!prefs.contains(Preferences.ONBOARDED) && prefs.contains(Preferences.SERVER_URL)) {
            val mode = if (prefs.getString(Preferences.USER_NAME, "").isNullOrEmpty()) AuthMode.NONE else AuthMode.BASIC
            prefs.edit {
                putString(Preferences.AUTH_MODE, mode.name)
                putBoolean(Preferences.ONBOARDED, true)
            }
        }
        _onboarded.value = prefs.getBoolean(Preferences.ONBOARDED, false)
        _signInRequired.value = authMode == AuthMode.OIDC && sessionToken == null
    }

    val serverUrl: String get() = prefs.getString(Preferences.SERVER_URL, "")!!

    val authMode: AuthMode
        get() = prefs.getString(Preferences.AUTH_MODE, null)
            ?.let { runCatching { AuthMode.valueOf(it) }.getOrNull() }
            ?: AuthMode.NONE

    val sessionToken: String? get() = secrets.getString(SESSION_TOKEN, null)

    val oidcUser: String? get() = secrets.getString(OIDC_USER, null)

    val canConnect: Boolean get() = onboarded.value && !signInRequired.value

    fun completeOnboarding(serverUrl: String, listName: String, mode: AuthMode, userName: String = "", password: String = "") {
        prefs.edit {
            putString(Preferences.SERVER_URL, serverUrl)
            putString(Preferences.LIST_NAME, listName)
            putString(Preferences.AUTH_MODE, mode.name)
            putString(Preferences.USER_NAME, userName)
            putString(Preferences.PASSWORD, password)
            putBoolean(Preferences.ONBOARDED, true)
        }
        _onboarded.value = true
        _signInRequired.value = mode == AuthMode.OIDC && sessionToken == null
    }

    @Synchronized
    fun storeSession(token: String, user: String) {
        secrets.edit(commit = true) {
            putString(SESSION_TOKEN, token)
            putString(OIDC_USER, user)
        }
        _signInRequired.value = false
    }

    /** Drops [rejectedToken] unless a newer session has replaced it. */
    @Synchronized
    fun markSignInRequired(rejectedToken: String?) {
        if (sessionToken != rejectedToken) return
        secrets.edit(commit = true) { remove(SESSION_TOKEN) }
        _signInRequired.value = true
    }

    fun storePendingLogin(login: PendingLogin) {
        secrets.edit(commit = true) {
            putString(PENDING_STATE, login.state)
            putString(PENDING_NONCE, login.nonce)
            putString(PENDING_VERIFIER, login.codeVerifier)
            putString(PENDING_SERVER, login.serverUrl)
            putString(PENDING_LIST, login.listName)
        }
    }

    fun takePendingLogin(): PendingLogin? {
        val state = secrets.getString(PENDING_STATE, null) ?: return null
        val nonce = secrets.getString(PENDING_NONCE, null) ?: return null
        val verifier = secrets.getString(PENDING_VERIFIER, null) ?: return null
        val server = secrets.getString(PENDING_SERVER, null) ?: return null
        val list = secrets.getString(PENDING_LIST, null)
        secrets.edit {
            remove(PENDING_STATE); remove(PENDING_NONCE); remove(PENDING_VERIFIER)
            remove(PENDING_SERVER); remove(PENDING_LIST)
        }
        return PendingLogin(state, nonce, verifier, server, list)
    }

    fun reset() {
        secrets.edit { clear() }
        prefs.edit {
            remove(Preferences.USER_NAME)
            remove(Preferences.PASSWORD)
            remove(Preferences.AUTH_MODE)
            putBoolean(Preferences.ONBOARDED, false)
        }
        _signInRequired.value = false
        _onboarded.value = false
    }

    companion object {
        const val SECRETS_FILE = "auth"
        private const val SESSION_TOKEN = "sessionToken"
        private const val OIDC_USER = "oidcUser"
        private const val PENDING_STATE = "pendingState"
        private const val PENDING_NONCE = "pendingNonce"
        private const val PENDING_VERIFIER = "pendingVerifier"
        private const val PENDING_SERVER = "pendingServer"
        private const val PENDING_LIST = "pendingList"
    }
}
