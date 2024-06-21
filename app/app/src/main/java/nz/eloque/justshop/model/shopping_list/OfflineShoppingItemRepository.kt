package nz.eloque.justshop.model.shopping_list

import kotlinx.coroutines.flow.Flow

class OfflineShoppingItemRepository(private val shoppingItemDao: ShoppingItemDao) :
    ShoppingItemRepository {

    override fun all(): Flow<List<ShoppingItem>> = shoppingItemDao.all()

    override suspend fun insert(shoppingItem: ShoppingItem) {
        shoppingItemDao.insert(shoppingItem)
    }

    override suspend fun insert(shoppingItems: Collection<ShoppingItem>) = shoppingItemDao.insert(shoppingItems.toList())

    override suspend fun deleteAllExcept(shoppingItems: Collection<ShoppingItem>) = shoppingItemDao.deleteAllExcept(shoppingItems.map { it.id }.toList())

    override suspend fun deleteAll() = shoppingItemDao.deleteAll()

    override suspend fun deleteChecked() = shoppingItemDao.deleteChecked()
}