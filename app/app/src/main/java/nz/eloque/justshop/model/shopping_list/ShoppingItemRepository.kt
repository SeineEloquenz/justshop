package nz.eloque.justshop.model.shopping_list

import kotlinx.coroutines.flow.Flow

interface ShoppingItemRepository {
    fun all(): Flow<List<ShoppingItem>>

    suspend fun insert(shoppingItem: ShoppingItem)

    suspend fun insert(shoppingItems: Collection<ShoppingItem>)

    suspend fun deleteAllExcept(shoppingItems: Collection<ShoppingItem>)

    suspend fun deleteAll()
    suspend fun deleteChecked()
}