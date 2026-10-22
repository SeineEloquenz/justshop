package nz.eloque.justshop.ui.onboarding

import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nz.eloque.justshop.Preferences
import nz.eloque.justshop.R
import nz.eloque.justshop.model.auth.AuthMode
import nz.eloque.justshop.model.auth.AuthStore
import nz.eloque.justshop.model.auth.OidcLogin

enum class OnboardingStep { SERVER, BASIC_CREDENTIALS, OIDC_SIGN_IN }

data class OnboardingUiState(
    val serverUrl: String = "",
    val listName: String = "",
    val step: OnboardingStep = OnboardingStep.SERVER,
    val busy: Boolean = false,
    @StringRes val error: Int? = null,
    val loginUrl: Uri? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authStore: AuthStore,
    private val oidcLogin: OidcLogin,
    prefs: SharedPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(
            serverUrl = prefs.getString(Preferences.SERVER_URL, null) ?: DEFAULT_SERVER,
            listName = prefs.getString(Preferences.LIST_NAME, null) ?: DEFAULT_LIST,
        )
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun updateServerUrl(url: String) = _uiState.update { it.copy(serverUrl = url, step = OnboardingStep.SERVER, error = null) }

    fun updateListName(name: String) = _uiState.update { it.copy(listName = name) }

    fun checkServer() = withBusy {
        val state = _uiState.value
        val mode = try {
            oidcLogin.authMode(serverUrl())
        } catch (e: Exception) {
            Log.w(TAG, "Server check failed", e)
            _uiState.update { it.copy(error = R.string.server_unreachable) }
            return@withBusy
        }
        when (mode) {
            AuthMode.NONE -> authStore.completeOnboarding(serverUrl(), state.listName, AuthMode.NONE)
            AuthMode.BASIC -> _uiState.update { it.copy(step = OnboardingStep.BASIC_CREDENTIALS) }
            AuthMode.OIDC -> _uiState.update { it.copy(step = OnboardingStep.OIDC_SIGN_IN) }
        }
    }

    fun submitBasic(userName: String, password: String) {
        authStore.completeOnboarding(serverUrl(), _uiState.value.listName, AuthMode.BASIC, userName, password)
    }

    fun signIn() = withBusy {
        try {
            val url = oidcLogin.start(serverUrl(), _uiState.value.listName)
            _uiState.update { it.copy(loginUrl = url) }
        } catch (e: Exception) {
            Log.w(TAG, "Starting login failed", e)
            _uiState.update { it.copy(error = R.string.login_failed) }
        }
    }

    fun loginLaunched() = _uiState.update { it.copy(loginUrl = null) }

    private fun serverUrl() = _uiState.value.serverUrl.trim().trimEnd('/')

    private fun withBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            try {
                block()
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        private const val TAG = "OnboardingViewModel"
        private const val DEFAULT_SERVER = "https://justshop.eloque.nz"
        private const val DEFAULT_LIST = "junkyard"
    }
}
