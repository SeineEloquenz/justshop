package nz.eloque.justshop.model.shopping_list

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nz.eloque.justshop.app.ApplicationScope
import nz.eloque.justshop.model.api.ShoppingListApi
import javax.inject.Inject
import javax.inject.Singleton

/** Serves the list from Room and mirrors every list the server pushes into it. */
@Singleton
class ShoppingListRepository @Inject constructor(
    private val shoppingItemDao: ShoppingItemDao,
    private val shoppingListApi: ShoppingListApi,
    @ApplicationScope scope: CoroutineScope,
) {
    init {
        scope.launch {
            shoppingListApi.listUpdates.collect { shoppingItemDao.replaceAll(it) }
        }
    }

    val items: Flow<List<ShoppingItem>> = shoppingItemDao.all()

    val connected: StateFlow<Boolean> = shoppingListApi.connected

    fun connect() = shoppingListApi.connect()

    fun disconnect() = shoppingListApi.disconnect()

    suspend fun update(shoppingItem: ShoppingItem): Boolean = shoppingListApi.update(shoppingItem)

    suspend fun deleteChecked() = shoppingListApi.deleteChecked()
}
