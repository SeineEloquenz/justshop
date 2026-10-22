package nz.eloque.justshop.model.shopping_list

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Transaction
    suspend fun replaceAll(shoppingItems: List<ShoppingItem>) {
        deleteAllExcept(shoppingItems.map { it.id })
        insert(shoppingItems)
    }

    @Query("SELECT * FROM shopping_items ORDER BY timestamp")
    fun all(): Flow<List<ShoppingItem>>

    @Query("DELETE from shopping_items")
    suspend fun deleteAll()

    @Query("DELETE from shopping_items WHERE checked = 1")
    suspend fun deleteChecked()
}