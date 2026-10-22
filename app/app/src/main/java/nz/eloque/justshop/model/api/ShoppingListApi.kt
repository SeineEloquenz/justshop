package nz.eloque.justshop.model.api

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nz.eloque.justshop.Preferences
import nz.eloque.justshop.app.ApplicationScope
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class AuthInterceptor(private val authStore: AuthStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val mode = authStore.authMode
        val builder = chain.request().newBuilder()
        val sessionToken = if (mode == AuthMode.OIDC) authStore.sessionToken else null
        when (mode) {
            AuthMode.BASIC -> {
                val username = authStore.basicUser
                val password = authStore.basicPassword
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

@Singleton
class ShoppingListApi @Inject constructor(
    private val prefs: SharedPreferences,
    private val authStore: AuthStore,
    okHttpClient: OkHttpClient,
    @ApplicationScope private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "ShoppingListApi"
    }

    private val _listUpdates = Channel<List<ShoppingItem>>(Channel.UNLIMITED)

    /** Full lists pushed by the server, in arrival order. Supports a single collector. */
    val listUpdates: Flow<List<ShoppingItem>> = _listUpdates.receiveAsFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    @Volatile
    private var websocket: WebSocket? = null
    private var reconnectJob: Job? = null

    private val serverUrlProvider: () -> String = { prefs.getString(Preferences.SERVER_URL, "")!! }
    private val apiVersion = "v2"

    private val baseUrlProvider = {
        serverUrlProvider.invoke() + "/" + apiVersion
    }

    private val mediaType = "application/json".toMediaType()
    private val client = okHttpClient.newBuilder()
        .addInterceptor(AuthInterceptor(authStore))
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    @Synchronized
    fun connect() {
        if (reconnectJob?.isActive == true) return
        Log.i(TAG, "Starting WebSocket Connection")
        reconnectJob = scope.launch {
            while (true) {
                // Checked under the lock so a socket cannot open after disconnect() returned.
                synchronized(this@ShoppingListApi) { if (isActive) openSocket() }
                delay(1000)
            }
        }
    }

    @Synchronized
    fun disconnect() {
        reconnectJob?.cancel()
        closeSocket()
    }

    @Synchronized
    fun closeSocket() {
        Log.d(TAG, "Socket closed")
        websocket?.cancel()
        websocket = null
        _connected.value = false
    }

    private fun openSocket() {
        if (websocket == null && authStore.canConnect) {
            Log.d(TAG, "Socket opened")
            websocket = client
                .newWebSocket(
                    Request.Builder().url(toApiUrl("${baseUrlProvider.invoke()}/ws")).build(),
                    ApiWebSocketListener(
                        onConnected = ::onSocketOpen,
                        onListUpdate = { _listUpdates.trySend(it.values.toList()) },
                        onConnectionLoss = ::onSocketLost,
                    )
                )
        }
    }

    // Synchronized so an early onOpen waits until openSocket() has assigned websocket.
    @Synchronized
    private fun onSocketOpen(socket: WebSocket) {
        if (socket === websocket) _connected.value = true
    }

    @Synchronized
    private fun onSocketLost(socket: WebSocket) {
        if (socket === websocket) closeSocket()
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

    private suspend fun post(url: String, json: String): Int = withContext(Dispatchers.IO) {
        val body: RequestBody = json.toRequestBody(mediaType)
        val request: Request = Request.Builder()
            .url(toApiUrl(url))
            .post(body)
            .build()
        client.newCall(request).execute().use { it.code }
    }

    private suspend fun delete(url: String): Int = withContext(Dispatchers.IO) {
        val request: Request = Request.Builder()
            .url(toApiUrl(url))
            .delete()
            .build()
        client.newCall(request).execute().use { it.code }
    }

    fun toApiUrl(url: String): HttpUrl {
        return url.toHttpUrl()
            .newBuilder()
            .addQueryParameter("list_name", prefs.getString(Preferences.LIST_NAME, "junkyard"))
            .build()
    }
}
