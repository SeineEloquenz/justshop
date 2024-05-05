package nz.eloque.justshop.model.shopping_list

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONObject
import java.util.UUID

@Entity(tableName = "shopping_items")
data class ShoppingItem(
    @PrimaryKey
    val id: UUID,
    val content: String,
    val checked: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            this.put("id", this@ShoppingItem.id)
            this.put("content", this@ShoppingItem.content)
            this.put("checked", this@ShoppingItem.checked)
            this.put("timestamp", this@ShoppingItem.timestamp)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ShoppingItem {
            return ShoppingItem(
                UUID.fromString(json.getString("id")),
                json.getString("content"),
                json.getBoolean("checked"),
                json.getLong("timestamp")
            )
        }
    }
}