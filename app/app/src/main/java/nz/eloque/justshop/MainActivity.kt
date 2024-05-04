package nz.eloque.justshop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import nz.eloque.justshop.ui.theme.ShoppingListTheme
import nz.eloque.justshop.ui.ShoppingList


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            ShoppingListTheme {
                ShoppingList(
                    this,
                    navController,
                )
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
