package nz.eloque.justshop.model.shopping_list

import kotlinx.coroutines.flow.Flow

class OfflineShoppingItemRepository(private val shoppingItemDao: ShoppingItemDao) :
    ShoppingItemRepository {

    override fun all(): Flow<List<ShoppingItem>> = shoppingItemDao.all()

    override suspend fun insert(shoppingItem: ShoppingItem) {
        shoppingItemDao.insert(shoppingItem)
    }

    override suspend fun replaceAll(shoppingItems: Collection<ShoppingItem>) = shoppingItemDao.replaceAll(shoppingItems.toList())

    override suspend fun deleteAll() = shoppingItemDao.deleteAll()

    override suspend fun deleteChecked() = shoppingItemDao.deleteChecked()
}