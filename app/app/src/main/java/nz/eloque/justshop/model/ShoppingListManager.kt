package nz.eloque.justshop.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import nz.eloque.justshop.model.api.ShoppingListApi
import nz.eloque.justshop.model.shopping_list.ShoppingItem
import nz.eloque.justshop.model.shopping_list.ShoppingItemRepository
import java.util.UUID

class ShoppingListManager(
    private val shoppingItemRepository: ShoppingItemRepository,
    private val shoppingListApi: ShoppingListApi,
): EmberObservable {
    private val observers = HashSet<EmberObserver>()

    init {
        shoppingListApi.connect()
    }

    fun handleApiUpdate(listUpdate: Map<UUID, ShoppingItem>) {
        CoroutineScope(Dispatchers.IO).launch {
            listUpdate.forEach { (_, value) ->
                shoppingItemRepository.insert(value)
            }
            notifyObservers()
        }
    }

    suspend fun update(shoppingItem: ShoppingItem) {
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