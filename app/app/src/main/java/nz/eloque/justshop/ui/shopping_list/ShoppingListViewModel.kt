package nz.eloque.justshop.ui.shopping_list

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nz.eloque.justshop.model.ShoppingListManager
import nz.eloque.justshop.model.auth.AuthStore
import nz.eloque.justshop.model.auth.OidcLogin
import nz.eloque.justshop.model.shopping_list.ShoppingItem
import javax.inject.Inject

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val shoppingListManager: ShoppingListManager,
    private val authStore: AuthStore,
    private val oidcLogin: OidcLogin,
) : ViewModel() {

    val items: StateFlow<List<ShoppingItem>> = shoppingListManager.messages().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val signInRequired: StateFlow<Boolean> = authStore.signInRequired

    private val _loginUrl = MutableStateFlow<Uri?>(null)
    val loginUrl: StateFlow<Uri?> = _loginUrl.asStateFlow()

    fun signIn() {
        viewModelScope.launch {
            try {
                _loginUrl.value = oidcLogin.start(authStore.serverUrl)
            } catch (e: Exception) {
                Log.w(TAG, "Starting login failed", e)
            }
        }
    }

    fun loginLaunched() {
        _loginUrl.value = null
    }

    suspend fun updateItem(shoppingItem: ShoppingItem) {
        shoppingListManager.update(shoppingItem)
    }

    suspend fun deleteAll() {
        shoppingListManager.deleteAll()
    }

    suspend fun deleteChecked() {
        shoppingListManager.deleteChecked()
    }

    companion object {
        private const val TAG = "ShoppingListViewModel"
    }
}
