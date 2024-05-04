package nz.eloque.justshop.model.shopping_list

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "shopping_items")
data class ShoppingItem(
    @PrimaryKey
    val id: UUID,
    val content: String,
    val checked: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)