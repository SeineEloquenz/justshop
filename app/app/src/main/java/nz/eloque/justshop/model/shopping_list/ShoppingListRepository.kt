package nz.eloque.justshop.model.shopping_list

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nz.eloque.justshop.model.api.ShoppingListApi

/** Serves the list from Room and mirrors every list the server pushes into it. */
@Singleton
class ShoppingListRepository @Inject constructor(
    private val shoppingItemDao: ShoppingItemDao,
    private val shoppingListApi: ShoppingListApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
