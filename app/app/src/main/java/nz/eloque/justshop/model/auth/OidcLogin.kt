package nz.eloque.justshop.model.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class LoginException(message: String) : Exception(message)

fun launchLogin(activityContext: Context, url: Uri) {
    CustomTabsIntent.Builder().build().launchUrl(activityContext, url)
}

@Singleton
class OidcLogin @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authStore: AuthStore,
    private val client: OkHttpClient,
) {
    private val jsonType = "application/json".toMediaType()
    private val redirectUri = "${context.packageName}://oauth"

    /** Asks the server how it authenticates. A proxy demanding basic auth answers before the backend. */
    suspend fun authMode(serverUrl: String): AuthMode = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${serverUrl.trimEnd('/')}/v2/auth-config").get().build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == 401 && response.header("WWW-Authenticate").orEmpty().startsWith("Basic", ignoreCase = true) -> AuthMode.BASIC
                !response.isSuccessful -> throw IOException("Server responded with ${response.code}")
                JSONObject(response.body.string()).optString("mode") == "oidc" -> AuthMode.OIDC
                else -> AuthMode.NONE
            }
        }
    }

    /**
     * Returns the provider's login page for [launchLogin]. The result arrives in [OAuthCallbackActivity].
     * Passing [onboardingListName] completes onboarding once the login succeeds.
     */
    suspend fun start(serverUrl: String, onboardingListName: String? = null): Uri {
        val pending = PendingLogin(randomToken(), randomToken(), randomToken(), serverUrl, onboardingListName)
        val body = JSONObject()
            .put("redirect_uri", redirectUri)
            .put("state", pending.state)
            .put("nonce", pending.nonce)
            .put("code_challenge", challenge(pending.codeVerifier))
        val url = post(serverUrl, "start", body).getString("url")
        authStore.storePendingLogin(pending)
        return url.toUri()
    }

    suspend fun finish(callback: Uri) {
        val pending = authStore.takePendingLogin() ?: throw LoginException("No login in progress")
        if (callback.getQueryParameter("state") != pending.state) {
            throw LoginException("State mismatch")
        }
        callback.getQueryParameter("error")?.let { throw LoginException(it) }
        val code = callback.getQueryParameter("code") ?: throw LoginException("Missing code")

        val body = JSONObject()
            .put("redirect_uri", redirectUri)
            .put("code", code)
            .put("code_verifier", pending.codeVerifier)
            .put("nonce", pending.nonce)
        val result = post(pending.serverUrl, "finish", body)
        authStore.storeSession(result.getString("token"), result.getString("user"))
        pending.listName?.let { authStore.completeOnboarding(pending.serverUrl, it, AuthMode.OIDC) }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val token = authStore.sessionToken ?: return@withContext
        val request = Request.Builder()
            .url("${authStore.serverUrl.trimEnd('/')}/v2/auth/logout")
            .header("Authorization", "Bearer $token")
            .post(ByteArray(0).toRequestBody())
            .build()
        runCatching { client.newCall(request).execute().close() }
    }

    private suspend fun post(serverUrl: String, step: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${serverUrl.trimEnd('/')}/v2/auth/oidc/$step")
            .post(body.toString().toRequestBody(jsonType))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw LoginException("Login $step failed with ${response.code}")
            }
            JSONObject(response.body.string())
        }
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
