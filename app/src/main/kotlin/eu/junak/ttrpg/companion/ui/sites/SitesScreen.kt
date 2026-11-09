package eu.junak.ttrpg.companion.ui.sites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.network.site.Site
import eu.junak.ttrpg.companion.core.network.site.SiteRepository
import eu.junak.ttrpg.companion.ui.components.roleLabel
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing
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
    val spacing = LocalSpacing.current
    val sites by viewModel.sites.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<Site?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.sites_title)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (sites.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.sites_add)) },
                )
            }
        },
    ) { padding ->
        if (sites.isEmpty()) {
            Welcome(Modifier.padding(padding), onAdd)
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            items(sites, key = Site::id) { site ->
                SiteCard(site, onOpen = { onOpen(site) }, onSignIn = { onSignIn(site) }, onRemove = { removing = site })
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

/** The first screen before any site is added. */
@Composable
private fun Welcome(modifier: Modifier, onAdd: () -> Unit) {
    val spacing = LocalSpacing.current
    Column(
        modifier.fillMaxSize().padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(spacing.portraitLarge),
        )
        Text(stringResource(R.string.sites_welcome), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(stringResource(R.string.sites_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Button(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.size(spacing.sm))
            Text(stringResource(R.string.sites_add))
        }
    }
}

@Composable
private fun SiteCard(site: Site, onOpen: () -> Unit, onSignIn: () -> Unit, onRemove: () -> Unit) {
    val spacing = LocalSpacing.current
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = spacing.md, top = spacing.md, bottom = spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(spacing.portrait).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(spacing.portrait + spacing.md),
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(site.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${roleLabel(site.role)} · ${site.baseUrl.substringAfter("://")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
        }
    }
}
