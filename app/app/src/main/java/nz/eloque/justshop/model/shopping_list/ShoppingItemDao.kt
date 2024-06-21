package nz.eloque.justshop.model.shopping_list

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface ShoppingItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(shoppingItem: ShoppingItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(shoppingItems: List<ShoppingItem>)

    @Query("DELETE FROM shopping_items WHERE id not in (:ids)")
    suspend fun deleteAllExcept(ids: List<UUID>)

    @Query("SELECT * FROM shopping_items")
    fun all(): Flow<List<ShoppingItem>>

    @Query("DELETE from shopping_items")
    suspend fun deleteAll()

    @Query("DELETE from shopping_items WHERE checked = 1")
    suspend fun deleteChecked()
}