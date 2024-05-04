package nz.eloque.justshop.model.shopping_list

import kotlinx.coroutines.flow.Flow

class OfflineShoppingItemRepository(private val shoppingItemDao: ShoppingItemDao) :
    ShoppingItemRepository {

    override fun all(): Flow<List<ShoppingItem>> = shoppingItemDao.all()

    override suspend fun insert(shoppingItem: ShoppingItem) {
        shoppingItemDao.insert(shoppingItem)
    }

    override suspend fun deleteAll() = shoppingItemDao.deleteAll()
}