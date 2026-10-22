package nz.eloque.justshop.model

import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import nz.eloque.justshop.model.api.ShoppingListApi
import nz.eloque.justshop.model.shopping_list.ShoppingItem
import nz.eloque.justshop.model.shopping_list.ShoppingItemRepository
import java.util.UUID

class ShoppingListManager @Inject constructor(
    private val shoppingItemRepository: ShoppingItemRepository,
    private val shoppingListApi: ShoppingListApi,
) {

    /** Must run on the caller's thread so updates are applied in the order they arrive. */
    fun handleApiUpdate(listUpdate: Map<UUID, ShoppingItem>) = runBlocking {
        shoppingItemRepository.replaceAll(listUpdate.values)
    }

    suspend fun update(shoppingItem: ShoppingItem): Boolean = shoppingListApi.update(shoppingItem)

    fun messages(): Flow<List<ShoppingItem>> = shoppingItemRepository.all()

    suspend fun deleteAll() {
        shoppingListApi.deleteAll()
    }

    suspend fun deleteChecked() {
        shoppingListApi.deleteChecked()
    }
}