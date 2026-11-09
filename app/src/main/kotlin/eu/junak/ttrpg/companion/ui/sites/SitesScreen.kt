package eu.junak.ttrpg.companion.ui.sites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.Role
import eu.junak.ttrpg.companion.core.network.site.Site
import eu.junak.ttrpg.companion.core.network.site.SiteRepository
import eu.junak.ttrpg.companion.ui.components.CenteredMessage
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SitesViewModel @Inject constructor(private val repository: SiteRepository) : ViewModel() {
    val sites = repository.sites

    fun remove(site: Site) {
        viewModelScope.launch { repository.remove(site) }
    }
}

/** The saved campaign sites. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitesScreen(
    onOpen: (Site) -> Unit,
    onAdd: () -> Unit,
    onSignIn: (Site) -> Unit,
    onSettings: () -> Unit,
    viewModel: SitesViewModel = hiltViewModel(),
) {
    val sites by viewModel.sites.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<Site?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sites_title)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.sites_add)) },
            )
        },
    ) { padding ->
        if (sites.isEmpty()) {
            Box(Modifier.padding(padding)) { CenteredMessage(stringResource(R.string.sites_empty)) }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(sites, key = Site::id) { site ->
                SiteRow(site, onOpen = { onOpen(site) }, onSignIn = { onSignIn(site) }, onRemove = { removing = site })
            }
        }
    }

    removing?.let { site ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.site_remove_title, site.name)) },
            text = { Text(stringResource(R.string.site_remove_text)) },
            confirmButton = {
                TextButton(onClick = { viewModel.remove(site); removing = null }) { Text(stringResource(R.string.site_remove)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SiteRow(site: Site, onOpen: () -> Unit, onSignIn: () -> Unit, onRemove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val role = stringResource(
        when (site.role) {
            Role.DM -> R.string.role_dm
            Role.PLAYER -> R.string.role_player
            null -> R.string.role_reader
        },
    )
    ListItem(
        headlineContent = { Text(site.name) },
        supportingContent = { Text("$role · ${site.baseUrl.substringAfter("://")}") },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_sign_in_again)) },
                        onClick = { menuOpen = false; onSignIn() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.site_remove)) },
                        onClick = { menuOpen = false; onRemove() },
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onOpen),
    )
}
