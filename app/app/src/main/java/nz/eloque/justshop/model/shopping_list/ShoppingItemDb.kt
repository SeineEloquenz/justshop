package nz.eloque.justshop.model.shopping_list

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ShoppingItem::class], version = 1, exportSchema = false)
abstract class ShoppingItemDb : RoomDatabase() {

    abstract fun dao(): ShoppingItemDao

    companion object {
        @Volatile
        private var Instance: ShoppingItemDb? = null

        fun getDb(context: Context): ShoppingItemDb {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, ShoppingItemDb::class.java, "shopping_item_db")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { Instance = it }
            }
        }
    }
}