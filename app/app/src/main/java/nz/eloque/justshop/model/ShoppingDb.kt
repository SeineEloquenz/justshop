package nz.eloque.justshop.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import nz.eloque.justshop.model.shopping_list.ShoppingItem
import nz.eloque.justshop.model.shopping_list.ShoppingItemDao

@Database(entities = [ShoppingItem::class], version = 1, exportSchema = false)
abstract class ShoppingDb : RoomDatabase() {

    abstract fun shoppingItemDao(): ShoppingItemDao

    companion object {
        @Volatile
        private var Instance: ShoppingDb? = null

        fun getDb(context: Context): ShoppingDb {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, ShoppingDb::class.java, "shopping_item_db")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { Instance = it }
            }
        }
    }
}