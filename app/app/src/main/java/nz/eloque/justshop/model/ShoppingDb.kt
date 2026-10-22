package nz.eloque.justshop.model

import androidx.room.Database
import androidx.room.RoomDatabase
import nz.eloque.justshop.model.shopping_list.ShoppingItem
import nz.eloque.justshop.model.shopping_list.ShoppingItemDao

@Database(entities = [ShoppingItem::class], version = 1, exportSchema = false)
abstract class ShoppingDb : RoomDatabase() {

    abstract fun shoppingItemDao(): ShoppingItemDao
}
