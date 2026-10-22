package nz.eloque.justshop.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nz.eloque.justshop.model.api.ShoppingListApi
import nz.eloque.justshop.model.auth.AuthMode
import nz.eloque.justshop.model.auth.AuthStore
import nz.eloque.justshop.model.auth.Connection
import nz.eloque.justshop.model.auth.OidcLogin
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val api: ShoppingListApi,
    private val authStore: AuthStore,
    private val oidcLogin: OidcLogin,
) : ViewModel() {

    val uiState: StateFlow<Connection> = authStore.connection

    fun updateListName(listName: String) {
        authStore.updateListName(listName)
        api.closeSocket()
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
}
