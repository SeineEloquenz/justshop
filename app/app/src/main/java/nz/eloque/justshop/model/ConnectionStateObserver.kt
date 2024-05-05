package nz.eloque.justshop.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

object ConnectionStateObserver {

    private val connectionStateFlow = MutableStateFlow(false)

    fun isConnected(): Flow<Boolean> {
        return connectionStateFlow
    }

    suspend fun updateConnectionState(state: Boolean) {
        connectionStateFlow.emit(state)
    }
}