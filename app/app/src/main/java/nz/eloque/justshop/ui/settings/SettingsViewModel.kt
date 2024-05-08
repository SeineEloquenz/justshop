package nz.eloque.justshop.ui.settings

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.eloque.justshop.Preferences
import nz.eloque.justshop.model.EmberObserver

data class SettingsUiState(
    val serverUrl: String = "",
    val syncInterval: Long = 1,
    val userName: String = "",
    val password: String = ""
)

class SettingsViewModel(
    private val prefs: SharedPreferences,
) : ViewModel(), EmberObserver {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        if (!prefs.contains(Preferences.SERVER_URL)) {
            updateServerUrl("https://justshop.eloque.nz")
        }
        if (!prefs.contains(Preferences.SYNC_INTERVAL)) {
            updateSyncInterval(1)
        }
        notifyOfChange()
    }

    override fun notifyOfChange() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                serverUrl = prefs.getString(Preferences.SERVER_URL, "")!!,
                syncInterval = prefs.getLong(Preferences.SYNC_INTERVAL, 1),
                userName = prefs.getString(Preferences.USER_NAME, "")!!,
                password = prefs.getString(Preferences.PASSWORD, "")!!,
            )
            Log.d(TAG, "Updated Settings!")
        }
    }

    fun updateServerUrl(url: String) {
        prefs.edit().putString(Preferences.SERVER_URL, url).apply()
        notifyOfChange()
    }

    fun updateSyncInterval(interval: Long) {
        prefs.edit().putLong(Preferences.SYNC_INTERVAL, interval).apply()
        notifyOfChange()
    }

    fun updateUserName(userName: String) {
        prefs.edit().putString(Preferences.USER_NAME, userName).apply()
        notifyOfChange()
    }

    fun updatePassword(password: String) {
        prefs.edit().putString(Preferences.PASSWORD, password).apply()
        notifyOfChange()
    }

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}