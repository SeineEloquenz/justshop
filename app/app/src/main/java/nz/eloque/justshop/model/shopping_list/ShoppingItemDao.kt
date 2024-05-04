package nz.eloque.justshop.model.shopping_list

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(shoppingItem: ShoppingItem)

    @Query("SELECT * FROM shopping_items")
    fun all(): Flow<List<ShoppingItem>>

    @Query("DELETE from shopping_items")
    suspend fun deleteAll()
}