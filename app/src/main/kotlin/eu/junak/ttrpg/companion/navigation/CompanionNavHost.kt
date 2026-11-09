package eu.junak.ttrpg.companion.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import eu.junak.ttrpg.companion.LiveUpdates
import eu.junak.ttrpg.companion.core.model.CampaignPage
import eu.junak.ttrpg.companion.ui.campaign.CampaignHomeScreen
import eu.junak.ttrpg.companion.ui.campaign.CollectionScreen
import eu.junak.ttrpg.companion.ui.campaign.RecordScreen
import eu.junak.ttrpg.companion.ui.campaign.SearchScreen
import eu.junak.ttrpg.companion.ui.campaign.SiteScope
import eu.junak.ttrpg.companion.ui.settings.SettingsScreen
import eu.junak.ttrpg.companion.ui.sites.AddSiteScreen
import eu.junak.ttrpg.companion.ui.sites.SitesScreen
import eu.junak.ttrpg.companion.ui.web.WebScreen

/**
 * The app's screens. While a site's screen is shown and the app is visible,
 * [liveUpdates] follows that site's event stream.
 */
@Composable
fun CompanionNavHost(liveUpdates: LiveUpdates, initialSiteId: String?) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val openSiteId = entry?.arguments?.getString(SITE_ID_ARGUMENT)
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(openSiteId) {
        if (openSiteId != null) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { liveUpdates.follow(openSiteId) }
    }
    LaunchedEffect(initialSiteId) {
        if (initialSiteId != null) navController.navigate(CampaignRoute(initialSiteId))
    }

    NavHost(navController, startDestination = SitesRoute) {
        composable<SitesRoute> {
            SitesScreen(
                onOpen = { navController.navigate(CampaignRoute(it.id)) },
                onAdd = { navController.navigate(AddSiteRoute()) },
                onSignIn = { navController.navigate(AddSiteRoute(it.id)) },
                onSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<AddSiteRoute> { backStack ->
            val route = backStack.toRoute<AddSiteRoute>()
            AddSiteScreen(
                onBack = navController::popBackStack,
                onDone = { site ->
                    if (route.siteId == null) {
                        navController.navigate(CampaignRoute(site.id)) { popUpTo(SitesRoute) }
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }
        composable<CampaignRoute> { backStack ->
            val siteId = backStack.toRoute<CampaignRoute>().siteId
            SiteScope {
                CampaignHomeScreen(
                    onBack = { navController.popBackStack(SitesRoute, inclusive = false) },
                    onOpenPage = { navController.navigate(CollectionRoute(siteId, it.id)) },
                    onOpenRecord = { page, key -> navController.navigate(RecordRoute(siteId, page.id, key)) },
                    onSearch = { navController.navigate(SearchRoute(siteId)) },
                    onOpenWebsite = { navController.navigate(WebRoute(siteId, it)) },
                    onSignIn = { navController.navigate(AddSiteRoute(siteId)) },
                    onSettings = { navController.navigate(SettingsRoute) },
                )
            }
        }
        composable<CollectionRoute> { backStack ->
            val route = backStack.toRoute<CollectionRoute>()
            val page = CampaignPage.byId(route.pageId) ?: return@composable LeaveUnknownPage(navController)
            SiteScope {
                CollectionScreen(
                    page = page,
                    onBack = navController::popBackStack,
                    onOpenRecord = { target, key -> navController.navigate(RecordRoute(route.siteId, target.id, key)) },
                )
            }
        }
        composable<RecordRoute> { backStack ->
            val route = backStack.toRoute<RecordRoute>()
            val page = CampaignPage.byId(route.pageId) ?: return@composable LeaveUnknownPage(navController)
            SiteScope {
                RecordScreen(
                    page = page,
                    key = route.key,
                    onBack = navController::popBackStack,
                    onOpenRecord = { target, key -> navController.navigate(RecordRoute(route.siteId, target.id, key)) },
                    onOpenWebsite = { navController.navigate(WebRoute(route.siteId, it)) },
                )
            }
        }
        composable<SearchRoute> { backStack ->
            val siteId = backStack.toRoute<SearchRoute>().siteId
            SiteScope {
                SearchScreen(
                    onBack = navController::popBackStack,
                    onOpenRecord = { page, key -> navController.navigate(RecordRoute(siteId, page.id, key)) },
                )
            }
        }
        composable<WebRoute> { backStack ->
            SiteScope { WebScreen(path = backStack.toRoute<WebRoute>().path, onBack = navController::popBackStack) }
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = navController::popBackStack)
        }
    }
}

/** Leaves a route naming a page this version does not know. */
@Composable
private fun LeaveUnknownPage(navController: NavHostController) {
    LaunchedEffect(Unit) { navController.popBackStack() }
}
