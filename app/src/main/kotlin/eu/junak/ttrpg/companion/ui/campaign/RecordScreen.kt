package eu.junak.ttrpg.companion.ui.campaign

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.ArticleLink
import eu.junak.ttrpg.companion.core.model.CampaignMarkdown
import eu.junak.ttrpg.companion.core.model.CampaignPage
import eu.junak.ttrpg.companion.core.model.RecordRef
import eu.junak.ttrpg.companion.core.model.Role
import eu.junak.ttrpg.companion.core.model.articleLink
import eu.junak.ttrpg.companion.core.model.details
import eu.junak.ttrpg.companion.core.model.listedFacts
import eu.junak.ttrpg.companion.core.model.mapPlace
import eu.junak.ttrpg.companion.ui.components.BackButton
import eu.junak.ttrpg.companion.ui.components.CenteredMessage
import eu.junak.ttrpg.companion.ui.components.RefRow
import eu.junak.ttrpg.companion.ui.components.SectionCard
import eu.junak.ttrpg.companion.ui.components.SectionHeader
import eu.junak.ttrpg.companion.ui.components.pageLabel
import eu.junak.ttrpg.companion.ui.components.readableWidth
import eu.junak.ttrpg.companion.ui.components.relatedLabel
import eu.junak.ttrpg.companion.ui.theme.ArticleTheme
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing

/**
 * One record: its portrait and name, structured facts, Markdown article,
 * known facts and questions, relationships, related records and the records
 * that mention it. Links to other records open natively; other pages of the
 * site open in the web view and other addresses in the browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    page: CampaignPage,
    key: String,
    onBack: () -> Unit,
    onOpenRecord: (CampaignPage, String) -> Unit,
    onOpenWebsite: (path: String) -> Unit,
    onEditEvent: (key: String) -> Unit,
    onOpenMap: (parentId: String?, focus: String?) -> Unit,
    onSignIn: () -> Unit,
    viewModel: CampaignViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val site by viewModel.site.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val baseUrl = site?.baseUrl.orEmpty()
    val dataset = state.dataset
    val dm = state.role == Role.DM
    val details = remember(dataset, page, key, dm) { dataset?.details(page, key, dm) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    LaunchedEffect(page, key, details != null) { if (details != null) viewModel.noteOpened(page, key) }
    val shownOn = remember(dataset, page, key) { dataset?.mapPlace(page, key) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                // The name heads the record; the bar names the page so it is not shown twice.
                title = { Text(pageLabel(page), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    // Events are the session notes the app edits; other records are edited on the website.
                    if (page == CampaignPage.EVENTS && site?.role != null && details != null) {
                        IconButton(onClick = { onEditEvent(key) }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.event_edit))
                        }
                    }
                    if (shownOn != null) {
                        IconButton(onClick = { onOpenMap(shownOn.parentId, key) }) {
                            Icon(Icons.Default.Map, contentDescription = stringResource(R.string.map_show))
                        }
                    }
                    IconButton(onClick = { onOpenWebsite(page.recordPath(key)) }) {
                        Icon(Icons.Default.Public, contentDescription = stringResource(R.string.record_open_website))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (dataset == null || details == null) {
            Column(Modifier.padding(padding)) {
                if (dataset == null) CampaignMissing(state, viewModel::refresh) else CenteredMessage(stringResource(R.string.record_missing))
            }
            return@Scaffold
        }
        val browser = LocalUriHandler.current
        val links = remember(baseUrl, browser) {
            object : UriHandler {
                override fun openUri(uri: String) {
                    when (val link = articleLink(uri, baseUrl)) {
                        is ArticleLink.Record -> onOpenRecord(link.page, link.key)
                        is ArticleLink.SitePage -> onOpenWebsite(link.path)
                        is ArticleLink.External -> browser.openUri(link.uri)
                    }
                }
            }
        }
        val open = { ref: RecordRef -> onOpenRecord(ref.page, ref.key) }
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.padding(padding)) {
            Column(Modifier.fillMaxSize().readableWidth().verticalScroll(rememberScrollState())) {
                CampaignStatus(state, onSignIn)
                Column(Modifier.padding(horizontal = spacing.md).padding(bottom = spacing.xl)) {
                    Hero(baseUrl, details)
                    val facts = details.listedFacts
                    if (facts.isNotEmpty()) {
                        Spacer(Modifier.size(spacing.md))
                        SectionCard { facts.forEach { FactRow(it) { ref -> open(ref) } } }
                    }
                    CompositionLocalProvider(LocalUriHandler provides links) {
                        Article(page, details, dataset, baseUrl)
                        if (details.privateNotes.isNotEmpty()) {
                            SectionHeader(stringResource(R.string.record_private_notes))
                            ArticleTheme {
                                Markdown(
                                    content = remember(details.privateNotes, dataset, baseUrl) {
                                        CampaignMarkdown.prepare(details.privateNotes, dataset, baseUrl)
                                    },
                                    imageTransformer = Coil3ImageTransformerImpl,
                                )
                            }
                        }
                    }
                    if (details.known.isNotEmpty()) {
                        SectionHeader(stringResource(R.string.record_known))
                        ArticleTheme {
                            details.known.forEach { fact ->
                                Row(Modifier.padding(vertical = spacing.xs)) {
                                    Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(spacing.md))
                                    Text(fact, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                    if (details.questions.isNotEmpty()) {
                        val unanswered = details.questions.count { it.answer.isEmpty() }
                        SectionHeader(
                            stringResource(R.string.record_questions),
                            trailing = pluralStringResource(R.plurals.record_questions_open, unanswered, unanswered, details.questions.size),
                        )
                        details.questions.forEach { QuestionRow(it) }
                    }
                    details.localMap?.let { LocalMap(baseUrl + it, details.summary.name) { onOpenMap(key, null) } }
                    if (details.relations.isNotEmpty()) {
                        SectionHeader(stringResource(R.string.record_relationships))
                        SectionCard {
                            details.relations.forEach { relation ->
                                val type = relation.type?.label ?: relation.typeId
                                RefRow(
                                    baseUrl,
                                    relation.other,
                                    supporting = listOf(
                                        stringResource(if (relation.outgoing) R.string.relation_to else R.string.relation_from, type),
                                        relation.label,
                                    ).filter(String::isNotEmpty).joinToString(" · "),
                                ) { open(relation.other) }
                            }
                        }
                    }
                    details.related.forEach { group ->
                        SectionHeader(relatedLabel(group.kind), trailing = group.records.size.toString())
                        SectionCard { group.records.forEach { ref -> RefRow(baseUrl, ref) { open(ref) } } }
                    }
                    if (details.tags.isNotEmpty()) Tags(details.tags)
                    if (details.mentionedIn.isNotEmpty()) {
                        SectionHeader(stringResource(R.string.record_mentioned_in), trailing = details.mentionedIn.size.toString())
                        SectionCard { details.mentionedIn.forEach { ref -> RefRow(baseUrl, ref, supporting = pageLabel(ref.page)) { open(ref) } } }
                    }
                }
            }
        }
    }
}
