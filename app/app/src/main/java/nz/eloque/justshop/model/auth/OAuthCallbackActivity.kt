package nz.eloque.justshop.model.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import nz.eloque.justshop.MainActivity
import nz.eloque.justshop.R
import javax.inject.Inject

@AndroidEntryPoint
class OAuthCallbackActivity : ComponentActivity() {

    @Inject lateinit var oidcLogin: OidcLogin

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val callback = intent?.data ?: return finish()

        lifecycleScope.launch {
            try {
                oidcLogin.finish(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Login failed", e)
                Toast.makeText(this@OAuthCallbackActivity, R.string.login_failed, Toast.LENGTH_LONG).show()
            }
            startActivity(
                Intent(this@OAuthCallbackActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
        }
    }

    companion object {
        private const val TAG = "OAuthCallbackActivity"
    }
}
