package nz.eloque.justshop.model.api

import android.util.Log
import nz.eloque.justshop.model.shopping_list.ShoppingItem
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID

class ApiWebSocketListener(
    private val onConnected: (WebSocket) -> Unit,
    private val onListUpdate: (Map<UUID, ShoppingItem>) -> Unit,
    private val onConnectionLoss: (WebSocket) -> Unit,
) : WebSocketListener() {

    override fun onOpen(webSocket: WebSocket, response: Response) {
        onConnected.invoke(webSocket)
        super.onOpen(webSocket, response)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.i(TAG, text)
        val json = JSONObject(text)
        val map = HashMap<UUID, ShoppingItem>()
        for (key in json.keys()) {
            val item = ShoppingItem.fromJson(json.getJSONObject(key))
            map[item.id] = item
        }
        onListUpdate.invoke(map)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e(TAG, "Connection to Server lost ", t)
        onConnectionLoss.invoke(webSocket)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.i(TAG, "Connection Closed: $code. Reason: $reason")
        onConnectionLoss.invoke(webSocket)
    }

    companion object {
        const val TAG = "ApiWebSocketListener"
    }
}
