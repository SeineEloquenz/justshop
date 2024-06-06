package nz.eloque.justshop.model.api

import android.util.Log
import nz.eloque.justshop.model.shopping_list.ShoppingItem
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class BasicAuthInterceptor(
    private val usernameProvider: () -> String,
    private val passwordProvider: () -> String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val username = usernameProvider.invoke()
        val password = passwordProvider.invoke()
        return if (username != "" && password != "") {
            val credentials = Credentials.basic(usernameProvider.invoke(), passwordProvider.invoke())
            val authenticatedRequest = req.newBuilder()
                .header("Authorization", credentials)
                .build()
            chain.proceed(authenticatedRequest)
        } else {
            chain.proceed(req)
        }

    }
}

class ShoppingListApi(
    serverUrlProvider: () -> String,
    usernameProvider: () -> String,
    passwordProvider: () -> String,
    private val onListUpdate: (Map<UUID, ShoppingItem>) -> Unit,
) {
    private val apiVersion = "v1"

    private val baseUrlProvider = {
        serverUrlProvider.invoke() + "/" + apiVersion
    }

    private val mediaType = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .addInterceptor(BasicAuthInterceptor(usernameProvider, passwordProvider))
        .build()

    fun connect() {
        Log.i(TAG, "Starting WebSocket Connection")
        val websocket = client
            .newWebSocket(
                Request.Builder().url("${baseUrlProvider.invoke()}/ws").build(),
                ApiWebSocketListener(onListUpdate) {
                    Thread.sleep(1000)
                    this.connect()
                }
            )
    }

    suspend fun deleteChecked() {
        try {
            val res = delete("${baseUrlProvider.invoke()}/delete-checked")
            if (res.code != 200) {
                Log.d(TAG, "Delete-Checked ${res.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call delete-checked", e)
        }
    }

    suspend fun deleteAll() {
        try {
            val res = delete("${baseUrlProvider.invoke()}/delete-checked")
            if (res.code != 200) {
                Log.d(TAG, "Delete-All ${res.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call delete-all", e)
        }
    }

    suspend fun update(item: ShoppingItem) {
        try {
            val json = item.toJson()
            val res = post("${baseUrlProvider.invoke()}/update", json.toString())
            if (res.code != 200) {
                Log.d(TAG, "Update ${res.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call update for item ${item.id}", e)
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
            .delete()
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