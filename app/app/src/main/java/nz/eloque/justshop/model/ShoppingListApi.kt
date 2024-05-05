package nz.eloque.justshop.model

import android.util.Log
import nz.eloque.justshop.model.shopping_list.ShoppingItem
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

class ShoppingListApi(
    private val serverUrlProvider: () -> String
) {
    private val mediaType = "application/json".toMediaType()
    private val client = OkHttpClient()

    suspend fun deleteChecked() {
        try {
            val res = delete("${serverUrlProvider.invoke()}/delete-checked")
            if (res.code != 200) {
                Log.d(TAG, "Delete-Checked ${res.code}")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to call delete-checked", e)
        }
    }

    suspend fun deleteAll() {
        try {
            val res = delete("${serverUrlProvider.invoke()}/delete-checked")
            if (res.code != 200) {
                Log.d(TAG, "Delete-All ${res.code}")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to call delete-all", e)
        }
    }

    suspend fun update(item: ShoppingItem) {
        try {
            val json = item.toJson()
            val res = post("${serverUrlProvider.invoke()}/update", json.toString())
            if (res.code != 200) {
                Log.d(TAG, "Update ${res.code}")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to call update for item ${item.id}", e)
        }
    }

    suspend fun all(): Map<UUID, ShoppingItem>? {
        return try {
            val resp = get("${serverUrlProvider.invoke()}/current")
            Log.d(TAG, "All ${resp.code}")
            return if (resp.code != 200) {
                null
            } else {
                val json = JSONObject(resp.body!!.string())
                val map = HashMap<UUID, ShoppingItem>()
                for (key in json.keys()) {
                    val item = ShoppingItem.fromJson(json.getJSONObject(key))
                    map[item.id] = item
                }
                ConnectionStateObserver.updateConnectionState(true)
                map
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to call current", e)
            ConnectionStateObserver.updateConnectionState(false)
            null
        }
    }

    @Suppress("RedundantSuspendModifier")
    private suspend fun post(url: String, json: String): Response {
        val body: RequestBody = json.toRequestBody(mediaType)
        val request: Request = Request.Builder()
            .url(url)
            .post(body)
            .build()
        return client.newCall(request).execute()
    }

    @Suppress("RedundantSuspendModifier")
    private suspend fun delete(url: String): Response {
        val request: Request = Request.Builder()
            .url(url)
            .build()
        return client.newCall(request).execute()
    }

    @Suppress("RedundantSuspendModifier")
    private suspend fun get(url: String): Response {
        val request: Request = Request.Builder()
            .url(url)
            .get()
            .build()
        return client.newCall(request).execute()
    }

    companion object {
        private const val TAG = "ShoppingListApi"
    }

    fun JSONArray.forEach(action: (JSONObject) -> Unit) {
        var i = 0
        while (i < this.length()) {
            val element = this.getJSONObject(i)
            action.invoke(element)
            i++
        }
    }
}