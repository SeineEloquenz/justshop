package nz.eloque.justshop.model.api

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nz.eloque.justshop.model.ConnectionStateObserver
import nz.eloque.justshop.model.ShoppingListManager
import nz.eloque.justshop.model.shopping_list.ShoppingItem
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID

class ApiWebSocketListener(
    private val onListUpdate: (Map<UUID, ShoppingItem>) -> Unit,
    private val onConnectionLoss: () -> Unit
) : WebSocketListener() {

    override fun onOpen(webSocket: WebSocket, response: Response) {
        CoroutineScope(Dispatchers.IO).launch {
            ConnectionStateObserver.updateConnectionState(true)
        }
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
        CoroutineScope(Dispatchers.IO).launch {
            ConnectionStateObserver.updateConnectionState(false)
        }
        Log.e(TAG, "Connection to Server lost ", t)
        onConnectionLoss.invoke()
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        CoroutineScope(Dispatchers.IO).launch {
            ConnectionStateObserver.updateConnectionState(true)
        }
        Log.i(TAG, "Connection Closed: $code. Reason: $reason")
        onConnectionLoss.invoke()
    }

    private fun scheduleReconnect() {

    }

    companion object {
        const val TAG = "ApiWebSocketListener"
    }
}