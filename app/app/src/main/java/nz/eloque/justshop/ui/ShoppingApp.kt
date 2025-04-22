package nz.eloque.justshop.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SignalCellularConnectedNoInternet0Bar
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nz.eloque.justshop.R
import nz.eloque.justshop.model.ConnectionStateObserver
import nz.eloque.justshop.ui.about.AboutView
import nz.eloque.justshop.ui.settings.SettingsView
import nz.eloque.justshop.ui.settings.SettingsViewModel
import nz.eloque.justshop.ui.shopping_list.ShoppingListView
import nz.eloque.justshop.ui.shopping_list.ShoppingListViewModel

sealed class Screen(val route: String, val icon: ImageVector, @StringRes val resourceId: Int) {
    data object ShoppingList : Screen("shoppingList", Icons.Default.ShoppingBag, R.string.justshop)
    data object Settings : Screen("settings", Icons.Default.Settings, R.string.settings)
    data object About : Screen("about", Icons.Default.Info, R.string.about)
}

@Composable
fun ShoppingList(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val shoppingListViewModel: ShoppingListViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val connectionStateFlow = ConnectionStateObserver.isConnected().collectAsState(false)

    Surface(
        modifier = modifier
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.ShoppingList.route,
        ) {
            composable(Screen.ShoppingList.route) {
                val listState = rememberLazyListState()
                ShoppingListScaffold(
                    navController = navController,
                    title = stringResource(id = R.string.justshop),
                    actions = {
                        IconButton(onClick = {
                            shoppingListViewModel.viewModelScope.launch(Dispatchers.IO) {
                                shoppingListViewModel.deleteChecked()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.remove_checked)
                            )
                        }
                    }
                ) {
                    if (!connectionStateFlow.value) {
                        Box(modifier = modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                imageVector = Icons.Default.SignalCellularConnectedNoInternet0Bar,
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.secondary),
                                contentDescription = stringResource(R.string.justshop),
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth(0.5f),
                                alpha = 0.25f
                            )
                        }
                    }
                    ShoppingListView(
                        shoppingListViewModel
                    )
                }
            }
            composable(Screen.Settings.route) {
                ShoppingListScaffold(
                    navController = navController,
                    title = stringResource(id = R.string.settings),
                    actions = {
                        IconButton(onClick = {
                            navController.navigate(Screen.About.route) }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.about)
                            )
                        }
                    }
                ) {
                    SettingsView(settingsViewModel)
                }
            }
            composable(Screen.About.route) {
                ShoppingListScaffold(
                    navController = navController,
                    title = stringResource(id = R.string.about),
                    toolWindow = true
                ) {
                    AboutView()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScaffold(
    navController: NavController,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.justshop),
    toolWindow: Boolean = false,
    showBack: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val items = listOf(
        Screen.ShoppingList,
        Screen.Settings,
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (toolWindow && showBack) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = actions
            )
        },
        bottomBar = {
            if (!toolWindow) {
                BottomAppBar {
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.route) },
                            label = { Text(stringResource(screen.resourceId)) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            } else {
                bottomBar.invoke()
            }
        },
        floatingActionButton = floatingActionButton
    ) { innerPadding ->
        Box(modifier = modifier
            .padding(innerPadding)
            .padding(10.dp)) {
            content.invoke()
        }
    }
}
