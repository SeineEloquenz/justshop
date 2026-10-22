package nz.eloque.justshop.app

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import nz.eloque.justshop.model.ShoppingDb
import nz.eloque.justshop.model.shopping_list.ShoppingItemDao
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun shoppingDb(@ApplicationContext context: Context): ShoppingDb {
        return Room.databaseBuilder(context, ShoppingDb::class.java, "shopping_item_db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun shoppingItemDao(shoppingDb: ShoppingDb): ShoppingItemDao {
        return shoppingDb.shoppingItemDao()
    }

    @Provides
    fun prefs(@ApplicationContext context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient()

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
