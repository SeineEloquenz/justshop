package nz.eloque.justshop.app

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import nz.eloque.justshop.ui.components.shopping_list.ShoppingListViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            ShoppingListViewModel(shoppingListApplication().container.shoppingListManager)
        }
    }
}

fun CreationExtras.shoppingListApplication(): ShoppingApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ShoppingApplication)
