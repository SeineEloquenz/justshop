package nz.eloque.justshop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import nz.eloque.justshop.model.auth.AuthStore
import nz.eloque.justshop.model.shopping_list.ShoppingListRepository
import nz.eloque.justshop.ui.ShoppingList
import nz.eloque.justshop.ui.theme.ShoppingListTheme
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var shoppingListRepository: ShoppingListRepository
    @Inject lateinit var authStore: AuthStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            ShoppingListTheme {
                ShoppingList(
                    navController,
                    authStore.onboarded,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        shoppingListRepository.connect()
    }

    override fun onStop() {
        super.onStop()
        shoppingListRepository.disconnect()
    }
}
