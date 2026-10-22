package nz.eloque.justshop.ui.settings

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.eloque.justshop.Preferences
import nz.eloque.justshop.model.api.ShoppingListApi
import nz.eloque.justshop.model.auth.AuthMode
import nz.eloque.justshop.model.auth.AuthStore
import nz.eloque.justshop.model.auth.OidcLogin

data class SettingsUiState(
    val serverUrl: String = "",
    val listName: String = "",
    val authMode: AuthMode = AuthMode.NONE,
    val userName: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val api: ShoppingListApi,
    private val prefs: SharedPreferences,
    private val authStore: AuthStore,
    private val oidcLogin: OidcLogin,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        notifyOfChange()
    }

    fun notifyOfChange() {
        viewModelScope.launch {
            val mode = authStore.authMode
            _uiState.value = _uiState.value.copy(
                listName = prefs.getString(Preferences.LIST_NAME, "")!!,
                serverUrl = authStore.serverUrl,
                authMode = mode,
                userName = when (mode) {
                    AuthMode.OIDC -> authStore.oidcUser.orEmpty()
                    else -> authStore.basicUser
                },
            )
            Log.d(TAG, "Updated Settings!")
        }
    }

    fun updateListName(listName: String) {
        prefs.edit { putString(Preferences.LIST_NAME, listName) }
        api.closeSocket()
        notifyOfChange()
    }

    fun changeServer() {
        viewModelScope.launch {
            if (authStore.authMode == AuthMode.OIDC) {
                oidcLogin.logout()
            }
            authStore.reset()
            api.closeSocket()
        }
    }

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}
