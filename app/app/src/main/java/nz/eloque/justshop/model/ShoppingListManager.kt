package nz.eloque.justshop.model

import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import nz.eloque.justshop.model.api.ShoppingListApi
import nz.eloque.justshop.model.shopping_list.ShoppingItem
import nz.eloque.justshop.model.shopping_list.ShoppingItemRepository
import java.util.UUID

class ShoppingListManager @Inject constructor(
    private val shoppingItemRepository: ShoppingItemRepository,
    private val shoppingListApi: ShoppingListApi,
) {

    fun handleApiUpdate(listUpdate: Map<UUID, ShoppingItem>) {
        val items = listUpdate.values
        CoroutineScope(Dispatchers.IO).launch {
            shoppingItemRepository.deleteAllExcept(items)
            shoppingItemRepository.insert(items)
        }
    }

    suspend fun update(shoppingItem: ShoppingItem) {
        shoppingListApi.update(shoppingItem)
    }

    fun messages(): Flow<List<ShoppingItem>> = shoppingItemRepository.all()

    suspend fun deleteAll() {
        shoppingListApi.deleteAll()
    }

    suspend fun deleteChecked() {
        shoppingListApi.deleteChecked()
    }

    companion object {
        private const val TAG = "ShoppingListManager"
    }
}