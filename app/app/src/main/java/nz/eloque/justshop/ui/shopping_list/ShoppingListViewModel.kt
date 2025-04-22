package nz.eloque.justshop.ui.shopping_list

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nz.eloque.justshop.model.EmberObserver
import nz.eloque.justshop.model.ShoppingListManager
import nz.eloque.justshop.model.shopping_list.ShoppingItem
import javax.inject.Inject


data class ShoppingItem(
    val id: Int,
    var name: String,
    var quantity: Int,
    var isEditing: Boolean = false
)

data class ShoppingListUiState(
    val items: List<ShoppingItem> = ArrayList()
)

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val shoppingListManager: ShoppingListManager,
) : ViewModel(), EmberObserver {

    private val _uiState = MutableStateFlow(ShoppingListUiState())
    val uiState: StateFlow<ShoppingListUiState> = _uiState.asStateFlow()

    init {
        updateList()
        shoppingListManager.register(this)
    }

    private fun updateList() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(items = shoppingListManager.messages().first())
        }
    }

    suspend fun updateItem(shoppingItem: ShoppingItem) {
        shoppingListManager.update(shoppingItem)
        updateList()
    }

    override fun notifyOfChange() {
        updateList()
        Log.d(TAG, "Got notification from repository!")
    }

    suspend fun deleteAll() {
        shoppingListManager.deleteAll()
        updateList()
    }

    suspend fun deleteChecked() {
        shoppingListManager.deleteChecked()
        updateList()
    }

    companion object {
        private const val TAG = "MessageViewModel"
    }
}