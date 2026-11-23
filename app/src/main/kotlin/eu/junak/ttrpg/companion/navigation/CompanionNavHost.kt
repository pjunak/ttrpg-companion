package eu.junak.ttrpg.companion.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
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
import eu.junak.ttrpg.companion.ui.map.MapScreen
import eu.junak.ttrpg.companion.ui.sessions.EventEditScreen
import eu.junak.ttrpg.companion.ui.sessions.SessionScreen
import eu.junak.ttrpg.companion.ui.sessions.SessionsScreen
import eu.junak.ttrpg.companion.ui.settings.SettingsScreen
import eu.junak.ttrpg.companion.ui.sheet.SheetScreen
import eu.junak.ttrpg.companion.ui.sites.AddSiteScreen
import eu.junak.ttrpg.companion.ui.sites.SitesScreen
import eu.junak.ttrpg.companion.ui.web.WebScreen

/**
 * The app's screens. While a site's screen is shown and the app is visible,
 * [liveUpdates] follows that site's event stream. [updatePending] marks the
 * ways into Settings while a newer app build waits there.
 */
@Composable
fun CompanionNavHost(liveUpdates: LiveUpdates, initialSiteId: String?, updatePending: Boolean) {
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
        composable<SitesRoute> { backStack ->
            SitesScreen(
                onOpen = { navController.open(backStack, CampaignRoute(it.id)) },
                onAdd = { navController.open(backStack, AddSiteRoute()) },
                onSignIn = { navController.open(backStack, AddSiteRoute(it.id)) },
                onSettings = { navController.open(backStack, SettingsRoute) },
                updatePending = updatePending,
            )
        }
        composable<AddSiteRoute> { backStack ->
            val route = backStack.toRoute<AddSiteRoute>()
            AddSiteScreen(
                onBack = { navController.leave(backStack) },
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
                    onBack = { if (backStack.isResumed) navController.popBackStack(SitesRoute, inclusive = false) },
                    onOpenPage = { navController.open(backStack, CollectionRoute(siteId, it.id)) },
                    onOpenRecord = { page, key -> navController.open(backStack, RecordRoute(siteId, page.id, key)) },
                    onOpenSession = { navController.open(backStack, SessionRoute(siteId, it)) },
                    onOpenSessions = { navController.open(backStack, SessionsRoute(siteId)) },
                    onOpenMap = { navController.open(backStack, MapRoute(siteId)) },
                    onSearch = { navController.open(backStack, SearchRoute(siteId)) },
                    onOpenWebsite = { navController.open(backStack, WebRoute(siteId, it)) },
                    onSignIn = { navController.open(backStack, AddSiteRoute(siteId)) },
                    onSettings = { navController.open(backStack, SettingsRoute) },
                    updatePending = updatePending,
                )
            }
        }
        composable<CollectionRoute> { backStack ->
            val route = backStack.toRoute<CollectionRoute>()
            val page = CampaignPage.byId(route.pageId) ?: return@composable LeaveUnknownPage(navController)
            SiteScope {
                CollectionScreen(
                    page = page,
                    onBack = { navController.leave(backStack) },
                    onOpenRecord = { target, key -> navController.open(backStack, RecordRoute(route.siteId, target.id, key)) },
                    onSignIn = { navController.open(backStack, AddSiteRoute(route.siteId)) },
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
                    onBack = { navController.leave(backStack) },
                    onOpenRecord = { target, key -> navController.open(backStack, RecordRoute(route.siteId, target.id, key)) },
                    onOpenWebsite = { navController.open(backStack, WebRoute(route.siteId, it)) },
                    onEditEvent = { navController.open(backStack, EventEditRoute(route.siteId, it)) },
                    onOpenMap = { parentId, focus -> navController.open(backStack, MapRoute(route.siteId, parentId, focus)) },
                    onSignIn = { navController.open(backStack, AddSiteRoute(route.siteId)) },
                    onOpenSheet = { navController.open(backStack, SheetRoute(route.siteId, route.key)) },
                )
            }
        }
        composable<SheetRoute> { backStack ->
            val route = backStack.toRoute<SheetRoute>()
            SiteScope {
                SheetScreen(
                    onBack = { navController.leave(backStack) },
                    onSignIn = { navController.open(backStack, AddSiteRoute(route.siteId)) },
                    onOpenWebsite = { navController.open(backStack, WebRoute(route.siteId, it)) },
                )
            }
        }
        composable<SearchRoute> { backStack ->
            val siteId = backStack.toRoute<SearchRoute>().siteId
            SiteScope {
                SearchScreen(
                    onBack = { navController.leave(backStack) },
                    onOpenRecord = { page, key -> navController.open(backStack, RecordRoute(siteId, page.id, key)) },
                )
            }
        }
        composable<SessionsRoute> { backStack ->
            val siteId = backStack.toRoute<SessionsRoute>().siteId
            SiteScope {
                SessionsScreen(
                    onBack = { navController.leave(backStack) },
                    onOpenSession = { navController.open(backStack, SessionRoute(siteId, it)) },
                    onSignIn = { navController.open(backStack, AddSiteRoute(siteId)) },
                )
            }
        }
        composable<SessionRoute> { backStack ->
            val route = backStack.toRoute<SessionRoute>()
            SiteScope {
                SessionScreen(
                    number = route.number,
                    // Called once from the screen's own leave state, never straight from a tap.
                    onBack = navController::popBackStack,
                    onOpenEvent = { navController.open(backStack, RecordRoute(route.siteId, CampaignPage.EVENTS.id, it)) },
                    onEditEvent = { navController.open(backStack, EventEditRoute(route.siteId, it)) },
                    onFullEditor = { note -> navController.open(backStack, EventEditRoute(route.siteId, sitting = route.number, note = note)) },
                    onSignIn = { navController.open(backStack, AddSiteRoute(route.siteId)) },
                )
            }
        }
        composable<EventEditRoute> { backStack ->
            val route = backStack.toRoute<EventEditRoute>()
            SiteScope {
                EventEditScreen(
                    // Both run once from the editor's outcome, never straight from a tap.
                    onClose = navController::popBackStack,
                    onDeleted = {
                        // Leave the deleted event's page too when the editor was opened from it.
                        val below = navController.previousBackStackEntry
                        val itsPage = below != null && below.destination.hasRoute<RecordRoute>() &&
                            below.toRoute<RecordRoute>().let { it.pageId == CampaignPage.EVENTS.id && it.key == route.key }
                        navController.popBackStack()
                        if (itsPage) navController.popBackStack()
                    },
                )
            }
        }
        composable<MapRoute> { backStack ->
            val siteId = backStack.toRoute<MapRoute>().siteId
            SiteScope {
                MapScreen(
                    onBack = { navController.leave(backStack) },
                    onOpenRecord = { page, key -> navController.open(backStack, RecordRoute(siteId, page.id, key)) },
                    onOpenWebsite = { navController.open(backStack, WebRoute(siteId, it)) },
                    onSignIn = { navController.open(backStack, AddSiteRoute(siteId)) },
                )
            }
        }
        composable<WebRoute> { backStack ->
            SiteScope { WebScreen(path = backStack.toRoute<WebRoute>().path, onBack = { navController.leave(backStack) }) }
        }
        composable<SettingsRoute> { backStack ->
            SettingsScreen(onBack = { navController.leave(backStack) })
        }
    }
}

/** Leaves a route naming a page this version does not know. */
@Composable
private fun LeaveUnknownPage(navController: NavHostController) {
    LaunchedEffect(Unit) { navController.popBackStack() }
}

/** Whether [this] is the screen in front; taps during a transition, such as a second tap, find it is not. */
private val NavBackStackEntry.isResumed: Boolean
    get() = lifecycle.currentState == Lifecycle.State.RESUMED

/** Opens [route] from [from] only while it is in front, so a double tap cannot push two screens. */
private fun NavHostController.open(from: NavBackStackEntry, route: Any) {
    if (from.isResumed) navigate(route)
}

/** Leaves [from] only while it is in front, so a double Back cannot pop the screen below it too. */
private fun NavHostController.leave(from: NavBackStackEntry) {
    if (from.isResumed) popBackStack()
}
