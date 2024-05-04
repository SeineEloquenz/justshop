package nz.eloque.justshop.app

import android.app.Application
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel


class ShoppingApplication : Application() {

    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        container = AppDataContainer(this)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        applicationScope.cancel("OnLowMemory() called by system")
    }

    companion object {
        val applicationScope = MainScope()
    }
}
