package nz.eloque.justshop.app

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import nz.eloque.justshop.model.ShoppingDb
import nz.eloque.justshop.model.shopping_list.OfflineShoppingItemRepository
import nz.eloque.justshop.model.shopping_list.ShoppingItemDao
import nz.eloque.justshop.model.shopping_list.ShoppingItemRepository

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun shoppingItemRepository(shoppingItemDao: ShoppingItemDao): ShoppingItemRepository {
        return OfflineShoppingItemRepository(shoppingItemDao)
    }

    @Provides
    fun shoppingDb(@ApplicationContext context: Context): ShoppingDb {
        return ShoppingDb.getDb(context)
    }

    @Provides
    fun shoppingItemDao(shoppingDb: ShoppingDb): ShoppingItemDao {
        return shoppingDb.shoppingItemDao()
    }

    @Provides
    fun prefs(@ApplicationContext context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }
}