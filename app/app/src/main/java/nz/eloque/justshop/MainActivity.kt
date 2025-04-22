package nz.eloque.justshop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import nz.eloque.justshop.model.api.ShoppingListApi
import nz.eloque.justshop.ui.ShoppingList
import nz.eloque.justshop.ui.theme.ShoppingListTheme
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var shoppingListApi: ShoppingListApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        shoppingListApi.ensureCreation()
        shoppingListApi.connect()

        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            ShoppingListTheme {
                ShoppingList(
                    navController,
                )
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
