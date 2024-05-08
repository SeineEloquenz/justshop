package nz.eloque.justshop.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import nz.eloque.justshop.model.api.ShoppingListApi
import nz.eloque.justshop.model.shopping_list.ShoppingItem
import nz.eloque.justshop.model.shopping_list.ShoppingItemRepository

class ShoppingListManager(
    private val shoppingItemRepository: ShoppingItemRepository,
    private val shoppingListApi: ShoppingListApi,
    private val syncIntervalProvider: () -> Long,
): EmberObservable {
    private val observers = HashSet<EmberObserver>()
    private val backgroundSyncTask = {
        while (true) {
            CoroutineScope(Dispatchers.IO).launch {
                val result = shoppingListApi.all()
                if (result != null) {
                    shoppingItemRepository.deleteAll()
                    result.forEach { (_, value) ->
                        shoppingItemRepository.insert(value)
                    }
                }
                notifyObservers()
            }
            Thread.sleep(1000 * syncIntervalProvider.invoke())
        }
    }

    init {
        Thread(backgroundSyncTask).start()
    }

    suspend fun update(shoppingItem: ShoppingItem) {
        shoppingItemRepository.insert(shoppingItem)
        shoppingListApi.update(shoppingItem)
    }

    fun messages(): Flow<List<ShoppingItem>> = shoppingItemRepository.all()

    override fun register(observer: EmberObserver) {
        observers.add(observer)
    }

    override fun notifyObservers() {
        observers.forEach { it.notifyOfChange() }
    }
    suspend fun deleteAll() {
        shoppingItemRepository.deleteAll()
        shoppingListApi.deleteAll()
    }

    suspend fun deleteChecked() {
        shoppingItemRepository.deleteChecked()
        shoppingListApi.deleteChecked()
    }

    companion object {
        private const val TAG = "ShoppingListManager"
    }
}