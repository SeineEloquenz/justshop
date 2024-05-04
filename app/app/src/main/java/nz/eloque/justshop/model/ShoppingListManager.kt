package nz.eloque.justshop.model

import kotlinx.coroutines.flow.Flow
import nz.eloque.justshop.model.shopping_list.ShoppingItem
import nz.eloque.justshop.model.shopping_list.ShoppingItemRepository

class ShoppingListManager(
    private val shoppingItemRepository: ShoppingItemRepository,
): EmberObservable {
    private val observers = HashSet<EmberObserver>()

    suspend fun update(shoppingItem: ShoppingItem) {
        shoppingItemRepository.insert(shoppingItem)
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
    }

    companion object {
        private const val TAG = "ShoppingListManager"
    }
}