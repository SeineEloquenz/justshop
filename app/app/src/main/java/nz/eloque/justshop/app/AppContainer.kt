package nz.eloque.justshop.app

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import nz.eloque.justshop.model.ShoppingListManager
import nz.eloque.justshop.model.shopping_list.OfflineShoppingItemRepository
import nz.eloque.justshop.model.shopping_list.ShoppingItemDb

interface AppContainer {
    val prefs: SharedPreferences
    val shoppingListManager: ShoppingListManager
}

class AppDataContainer(private val context: Context) : AppContainer {

    override val prefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    override val shoppingListManager: ShoppingListManager by lazy {
        ShoppingListManager(
            OfflineShoppingItemRepository(ShoppingItemDb.getDb(context).dao())
        )
    }
}
