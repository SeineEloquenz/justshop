package nz.eloque.justshop.ui.shopping_list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import nz.eloque.justshop.model.ShoppingListManager
import nz.eloque.justshop.model.shopping_list.ShoppingItem
import javax.inject.Inject

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val shoppingListManager: ShoppingListManager,
) : ViewModel() {

    val items: StateFlow<List<ShoppingItem>> = shoppingListManager.messages().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    suspend fun updateItem(shoppingItem: ShoppingItem) {
        shoppingListManager.update(shoppingItem)
    }

    suspend fun deleteAll() {
        shoppingListManager.deleteAll()
    }

    suspend fun deleteChecked() {
        shoppingListManager.deleteChecked()
    }
}