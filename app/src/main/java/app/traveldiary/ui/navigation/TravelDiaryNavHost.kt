package app.traveldiary.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.traveldiary.AppContainer
import app.traveldiary.R
import app.traveldiary.ui.editor.VisitEditorScreen
import app.traveldiary.ui.editor.VisitEditorViewModel
import app.traveldiary.ui.home.HomeEditorScreen
import app.traveldiary.ui.home.HomeEditorViewModel
import app.traveldiary.ui.home.WelcomeHomeScreen
import app.traveldiary.ui.map.MapScreen
import app.traveldiary.ui.map.MapViewModel
import app.traveldiary.ui.place.PlaceScreen
import app.traveldiary.ui.place.PlaceViewModel
import app.traveldiary.ui.places.PlacesScreen
import app.traveldiary.ui.places.PlacesViewModel
import app.traveldiary.ui.search.SearchScreen
import app.traveldiary.ui.search.SearchViewModel
import app.traveldiary.ui.settings.SettingsScreen
import app.traveldiary.ui.settings.SettingsViewModel
import app.traveldiary.ui.stats.StatsScreen
import app.traveldiary.ui.stats.StatsViewModel

/** Saved on the map's back stack entry to make it fly to a place that was just added. */
private const val FOCUS_PLACE_KEY = "focusPlaceId"

private enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    MAP(MapRoute, R.string.nav_map, Icons.Outlined.Map, Icons.Filled.Map),
    PLACES(PlacesRoute, R.string.nav_places, Icons.Outlined.Book, Icons.Filled.Book),
    STATS(StatsRoute, R.string.nav_stats, Icons.Outlined.BarChart, Icons.Filled.BarChart),
    SETTINGS(SettingsRoute, R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}

@Composable
fun TravelDiaryNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTopLevel = TopLevelDestination.entries.firstOrNull { destination ->
        backStackEntry?.destination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
    }

    fun openTopLevel(destination: TopLevelDestination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Ask where the user lives on the very first launch only, not after every update.
    LaunchedEffect(Unit) {
        if (!container.settings.homeAsked) {
            container.settings.markHomeAsked()
            navController.navigate(WelcomeRoute)
        }
    }

    Scaffold(
        bottomBar = {
            if (currentTopLevel != null) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = destination == currentTopLevel
                        NavigationBarItem(
                            selected = selected,
                            onClick = { openTopLevel(destination) },
                            icon = {
                                Icon(if (selected) destination.selectedIcon else destination.icon, contentDescription = null)
                            },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MapRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable<MapRoute> { entry ->
                val focusPlaceId by entry.savedStateHandle
                    .getStateFlow<Long?>(FOCUS_PLACE_KEY, null)
                    .collectAsStateWithLifecycle()
                MapScreen(
                    viewModel = viewModel { MapViewModel(container) },
                    focusPlaceId = focusPlaceId,
                    onFocusHandled = { entry.savedStateHandle[FOCUS_PLACE_KEY] = null },
                    onSearch = { countryCode -> navController.navigate(SearchRoute(countryCode)) },
                    onOpenPlace = { placeId -> navController.navigate(PlaceRoute(placeId)) },
                    onAddCustomPlace = { latitude, longitude ->
                        navController.navigate(VisitEditorRoute(latitude = latitude, longitude = longitude))
                    },
                )
            }
            composable<SearchRoute> { entry ->
                val route = entry.toRoute<SearchRoute>()
                SearchScreen(
                    viewModel = viewModel { SearchViewModel(container, route.countryCode) },
                    onBack = { navController.popBackStack() },
                    onCitySelected = { cityId ->
                        navController.navigate(
                            if (route.forHome) HomeEditorRoute(cityId = cityId) else VisitEditorRoute(cityId = cityId),
                        )
                    },
                    onOpenSettings = { openTopLevel(TopLevelDestination.SETTINGS) },
                )
            }
            composable<WelcomeRoute> {
                WelcomeHomeScreen(
                    onChooseHome = { navController.navigate(SearchRoute(forHome = true)) },
                    onSkip = { navController.popBackStack() },
                )
            }
            composable<HomeEditorRoute> { entry ->
                val route = entry.toRoute<HomeEditorRoute>()
                HomeEditorScreen(
                    viewModel = viewModel { HomeEditorViewModel(container, route) },
                    onClose = { navController.popBackStack() },
                    onDone = {
                        // Back to where it started: Settings, or the map after the welcome screen.
                        if (!navController.popBackStack<SettingsRoute>(inclusive = false)) {
                            navController.popBackStack<MapRoute>(inclusive = false)
                        }
                    },
                )
            }
            composable<VisitEditorRoute> { entry ->
                val route = entry.toRoute<VisitEditorRoute>()
                val addedFromMap = route.cityId != null || route.latitude != null
                VisitEditorScreen(
                    viewModel = viewModel { VisitEditorViewModel(container, route) },
                    onClose = { navController.popBackStack() },
                    onSaved = { placeId ->
                        if (addedFromMap) {
                            navController.getBackStackEntry<MapRoute>().savedStateHandle[FOCUS_PLACE_KEY] = placeId
                            navController.popBackStack<MapRoute>(inclusive = false)
                        } else {
                            navController.popBackStack()
                        }
                    },
                )
            }
            composable<PlaceRoute> { entry ->
                val route = entry.toRoute<PlaceRoute>()
                PlaceScreen(
                    viewModel = viewModel { PlaceViewModel(container, route.placeId) },
                    onBack = { navController.popBackStack() },
                    onAddVisit = { navController.navigate(VisitEditorRoute(placeId = route.placeId)) },
                    onEditVisit = { visitId -> navController.navigate(VisitEditorRoute(visitId = visitId)) },
                )
            }
            composable<PlacesRoute> {
                PlacesScreen(
                    viewModel = viewModel { PlacesViewModel(container) },
                    onOpenPlace = { placeId -> navController.navigate(PlaceRoute(placeId)) },
                    onAddPlace = { navController.navigate(SearchRoute()) },
                )
            }
            composable<StatsRoute> {
                StatsScreen(viewModel = viewModel { StatsViewModel(container) })
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    viewModel = viewModel { SettingsViewModel(container) },
                    onAddHome = { navController.navigate(SearchRoute(forHome = true)) },
                    onEditHome = { periodId -> navController.navigate(HomeEditorRoute(periodId = periodId)) },
                )
            }
        }
    }
}
