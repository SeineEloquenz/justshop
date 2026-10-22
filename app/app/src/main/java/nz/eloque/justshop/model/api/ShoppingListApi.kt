package nz.eloque.justshop.model.api

import android.content.SharedPreferences
import android.util.Log
import jakarta.inject.Inject
import jakarta.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nz.eloque.justshop.Preferences
import nz.eloque.justshop.model.ShoppingListManager
import nz.eloque.justshop.model.auth.AuthMode
import nz.eloque.justshop.model.auth.AuthStore
import nz.eloque.justshop.model.shopping_list.ShoppingItem
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AuthInterceptor(
    private val authStore: AuthStore,
    private val prefs: SharedPreferences,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val mode = authStore.authMode
        val builder = chain.request().newBuilder()
        val sessionToken = if (mode == AuthMode.OIDC) authStore.sessionToken else null
        when (mode) {
            AuthMode.BASIC -> {
                val username = prefs.getString(Preferences.USER_NAME, "")!!
                val password = prefs.getString(Preferences.PASSWORD, "")!!
                if (username != "" && password != "") {
                    builder.header("Authorization", Credentials.basic(username, password))
                }
            }
            AuthMode.OIDC -> sessionToken?.let { builder.header("Authorization", "Bearer $it") }
            AuthMode.NONE -> {}
        }
        val response = chain.proceed(builder.build())
        if (response.code == 401 && mode == AuthMode.OIDC) {
            authStore.markSignInRequired(sessionToken)
        }
        return response
    }
}

class ShoppingListApi @Inject constructor(
    private val shoppingListManager: Provider<ShoppingListManager>,
    private val prefs: SharedPreferences,
    private val authStore: AuthStore,
) {
    companion object {
        private const val TAG = "ShoppingListApi"

        private var websocket: WebSocket? = null
        private var reconnectJob: Job? = null
    }

    private val serverUrlProvider: () -> String = { prefs.getString(Preferences.SERVER_URL, "")!! }
    private val apiVersion = "v2"

    private val baseUrlProvider = {
        serverUrlProvider.invoke() + "/" + apiVersion
    }

    private val mediaType = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(authStore, prefs))
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    fun connect() {
        if (reconnectJob?.isActive == true) return
        Log.i(TAG, "Starting WebSocket Connection")
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                openSocket()
                delay(1000)
            }
        }
    }

    fun closeSocket() {
        Log.d(TAG, "Socket closed")
        websocket?.cancel()
        websocket = null
    }

    private fun openSocket() {
        if (websocket == null && authStore.canConnect) {
            Log.d(TAG, "Socket opened")
            websocket = client
                .newWebSocket(
                    Request.Builder().url(toApiUrl("${baseUrlProvider.invoke()}/ws")).build(),
                    ApiWebSocketListener(shoppingListManager.get()::handleApiUpdate) {
                        closeSocket()
                    }
                )
        }
    }

    suspend fun deleteChecked() {
        try {
            val code = delete("${baseUrlProvider.invoke()}/delete-checked")
            if (code != 200) {
                Log.d(TAG, "Delete-Checked $code")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call delete-checked", e)
        }
    }

    suspend fun deleteAll() {
        try {
            val code = delete("${baseUrlProvider.invoke()}/delete-all")
            if (code != 200) {
                Log.d(TAG, "Delete-All $code")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call delete-all", e)
        }
    }

    suspend fun update(item: ShoppingItem): Boolean {
        return try {
            val json = item.toJson()
            val code = post("${baseUrlProvider.invoke()}/update", json.toString())
            if (code != 200) {
                Log.d(TAG, "Update $code")
            }
            code == 200
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call update for item ${item.id}", e)
            false
        }
    }

    @Suppress("RedundantSuspendModifier")
    private suspend fun post(url: String, json: String): Int {
        val body: RequestBody = json.toRequestBody(mediaType)
        val request: Request = Request.Builder()
            .url(toApiUrl(url))
            .post(body)
            .build()
        return client.newCall(request).execute().use { it.code }
    }

    @Suppress("RedundantSuspendModifier")
    private suspend fun delete(url: String): Int {
        val request: Request = Request.Builder()
            .url(toApiUrl(url))
            .delete()
            .build()
        return client.newCall(request).execute().use { it.code }
    }

    @Suppress("RedundantSuspendModifier")
    private suspend fun get(url: String): Response {
        val request: Request = Request.Builder()
            .url(toApiUrl(url))
            .get()
            .build()
        return client.newCall(request).execute()
    }

    fun JSONArray.forEach(action: (JSONObject) -> Unit) {
        var i = 0
        while (i < this.length()) {
            val element = this.getJSONObject(i)
            action.invoke(element)
            i++
        }
    }

    fun ensureCreation() {
    }

    fun toApiUrl(url: String): HttpUrl {
        return url.toHttpUrl()
            .newBuilder()
            .addQueryParameter("list_name", prefs.getString(Preferences.LIST_NAME, "junkyard"))
            .build()
    }
}