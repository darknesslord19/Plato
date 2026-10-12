package com.kaan.play.ui.nav

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kaan.play.R
import com.kaan.play.data.model.MediaType
import com.kaan.play.ui.addons.AddonsScreen
import com.kaan.play.ui.detail.DetailScreen
import com.kaan.play.ui.home.HomeScreen
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.search.SearchScreen
import com.kaan.play.ui.settings.AboutScreen
import com.kaan.play.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private const val ADDONS_ROUTE = "settings/addons?add={add}"
private const val ABOUT_ROUTE = "settings/about"
private const val DETAIL_ROUTE = "detail/{type}/{id}"

private fun addonsRoute(startAdding: Boolean) = "settings/addons?add=$startAdding"
private fun detailRoute(type: MediaType, id: Int) = "detail/${type.tmdbPath}/$id"

enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Home("home", R.string.tab_home, PlayIcons.Home),
    Search("search", R.string.tab_search, PlayIcons.Search),
    Library("library", R.string.tab_library, PlayIcons.Library),
    Settings("settings", R.string.tab_settings, PlayIcons.Settings),
}

@Composable
fun PlayRoot() {
    val navController = rememberNavController()
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    fun openTab(tab: Tab) {
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openDetail(type: MediaType, id: Int) = navController.navigate(detailRoute(type, id))

    // Eklentiler ekranı Ayarlar sekmesine aittir: nereden gelinirse gelinsin önce o sekmeye geçilir,
    // yoksa ekran çağıran sekmenin yığınına girer ve o sekme hep onu açar.
    fun openAddons(startAdding: Boolean) {
        openTab(Tab.Settings)
        navController.navigate(addonsRoute(startAdding)) { launchSingleTop = true }
    }

    // https manifest bağlantısıyla açılınca (MainActivity -> pendingAddonUrl) ekleme ekranına git.
    val pendingAddonUrl by container.pendingAddonUrl.collectAsStateWithLifecycle()
    LaunchedEffect(pendingAddonUrl) {
        if (pendingAddonUrl != null) openAddons(startAdding = false)
    }

    // Kaynak seçimi 5. adımda gelir. Eklenti yoksa oynatmanın tek yolu eklenti eklemektir.
    fun play() {
        scope.launch {
            if (container.addons.enabledAddons().isEmpty()) openAddons(startAdding = true)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Detay tam ekran: alt menü gizli.
            if (currentRoute != DETAIL_ROUTE) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route || currentRoute?.startsWith(tab.route + "/") == true,
                            onClick = { openTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = stringResource(tab.label)) },
                            // Büyük yazıda/uzun Türkçe etiketlerde dört sekmeye sığması için yalnızca seçili sekme etiket gösterir.
                            alwaysShowLabel = false,
                            label = { Text(stringResource(tab.label), maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Home.route) {
                HomeScreen(
                    onOpenDetail = ::openDetail,
                    onAddAddon = { openAddons(startAdding = true) },
                )
            }
            composable(Tab.Search.route) { SearchScreen(onOpenDetail = ::openDetail) }
            composable(Tab.Library.route) { Placeholder(Tab.Library.label) }
            composable(Tab.Settings.route) {
                SettingsScreen(
                    onOpenAddons = { navController.navigate(addonsRoute(false)) },
                    onOpenAbout = { navController.navigate(ABOUT_ROUTE) },
                )
            }
            composable(
                route = ADDONS_ROUTE,
                arguments = listOf(navArgument("add") { type = NavType.BoolType; defaultValue = false }),
            ) { entry ->
                AddonsScreen(
                    startAdding = entry.arguments?.getBoolean("add") == true,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ABOUT_ROUTE) { AboutScreen(onBack = { navController.popBackStack() }) }
            composable(
                route = DETAIL_ROUTE,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("id") { type = NavType.IntType },
                ),
            ) { entry ->
                val type = MediaType.fromTmdb(entry.arguments?.getString("type")) ?: MediaType.Movie
                val id = entry.arguments?.getInt("id") ?: 0
                DetailScreen(
                    type = type,
                    id = id,
                    onBack = { navController.popBackStack() },
                    onPlay = { _, _ -> play() },
                )
            }
        }
    }
}

@Composable
private fun Placeholder(@StringRes title: Int) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.placeholder_coming_soon),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
