package eu.junak.ttrpg.companion.ui.campaign

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.CampaignDataset
import eu.junak.ttrpg.companion.core.model.CampaignMarkdown
import eu.junak.ttrpg.companion.core.model.CampaignPage
import eu.junak.ttrpg.companion.core.model.Fact
import eu.junak.ttrpg.companion.core.model.FactKind
import eu.junak.ttrpg.companion.core.model.Question
import eu.junak.ttrpg.companion.core.model.RecordDetails
import eu.junak.ttrpg.companion.core.model.ArticleLink
import eu.junak.ttrpg.companion.core.model.RecordRef
import eu.junak.ttrpg.companion.core.model.articleLink
import eu.junak.ttrpg.companion.core.model.listedFacts
import eu.junak.ttrpg.companion.core.model.mapPlace
import eu.junak.ttrpg.companion.core.model.Role
import eu.junak.ttrpg.companion.core.model.details
import eu.junak.ttrpg.companion.core.model.text
import eu.junak.ttrpg.companion.ui.components.BackButton
import eu.junak.ttrpg.companion.ui.components.CenteredMessage
import eu.junak.ttrpg.companion.ui.components.Portrait
import eu.junak.ttrpg.companion.ui.components.RefRow
import eu.junak.ttrpg.companion.ui.components.SectionCard
import eu.junak.ttrpg.companion.ui.components.SectionHeader
import eu.junak.ttrpg.companion.ui.components.StatusDot
import eu.junak.ttrpg.companion.ui.components.factLabel
import eu.junak.ttrpg.companion.ui.components.fieldLabel
import eu.junak.ttrpg.companion.ui.components.pageLabel
import eu.junak.ttrpg.companion.ui.components.partyName
import eu.junak.ttrpg.companion.ui.components.relatedLabel
import eu.junak.ttrpg.companion.ui.theme.ArticleTheme
import eu.junak.ttrpg.companion.ui.theme.Emphasis
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing
import eu.junak.ttrpg.companion.ui.theme.optionColor

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
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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

/** Portrait, name, subtitle and the status and attitude badges. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(baseUrl: String, details: RecordDetails) {
    val spacing = LocalSpacing.current
    val summary = details.summary
    Column(
        Modifier.fillMaxWidth().padding(top = spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        val size = if (summary.portrait != null) spacing.portraitLarge else spacing.heroSymbol
        Portrait(baseUrl, summary.portrait, summary.name, summary.page, size, summary.icon, ring = true)
        Text(
            summary.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        if (summary.subtitle.isNotEmpty()) {
            Text(
                summary.subtitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        val status = details.facts.firstOrNull { it.kind == FactKind.STATUS }
        if (status != null || details.attitudes.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.CenterHorizontally)) {
                status?.let { Badge(it.text, it.color, it.icon) }
                details.attitudes.forEach { Badge(it.label, it.color, "") }
            }
        }
    }
}

/** A pill with an option's colour, as the website shows statuses and attitudes. */
@Composable
private fun Badge(label: String, color: Long?, icon: String) {
    val spacing = LocalSpacing.current
    val tint = optionColor(color, MaterialTheme.colorScheme.primary)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(spacing.hairline, tint.copy(alpha = Emphasis.BADGE_OUTLINE)),
    ) {
        Row(
            Modifier.padding(horizontal = spacing.md - spacing.xs, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs),
        ) {
            if (icon.isEmpty()) StatusDot(tint) else Text(icon, color = tint, style = MaterialTheme.typography.labelLarge)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** One labelled fact; linked values open their record. */
@Composable
private fun FactRow(fact: Fact, onOpen: (RecordRef) -> Unit) {
    val spacing = LocalSpacing.current
    val link = fact.link
    var modifier = Modifier.fillMaxWidth()
    if (link != null) modifier = modifier.clickable { onOpen(link) }
    Row(modifier.padding(horizontal = spacing.md, vertical = spacing.sm + spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        Text(
            factLabel(fact.kind),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Row(Modifier.weight(0.6f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            if (fact.color != null) StatusDot(optionColor(fact.color, MaterialTheme.colorScheme.primary))
            val flag = fact.flag
            val text = when {
                flag != null -> stringResource(if (flag) R.string.fact_yes else R.string.fact_no)
                fact.kind == FactKind.FACTION || fact.kind == FactKind.OWNER -> partyName(fact.text)
                else -> fact.text
            }
            Text(
                listOf(fact.icon.takeIf { fact.color == null }.orEmpty(), text).filter(String::isNotEmpty).joinToString(" "),
                style = MaterialTheme.typography.bodyLarge,
                color = if (link != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (link != null) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(spacing.md),
                )
            }
        }
    }
}

/** The record's Markdown fields; the first has no heading, the rest are named. */
@Composable
private fun Article(page: CampaignPage, details: RecordDetails, dataset: CampaignDataset, baseUrl: String) {
    val record = dataset.record(page.collection, details.summary.key) ?: return
    val fields = page.articleFields.filter { record.value.text(it).isNotEmpty() }
    // An event's short note is its subtitle when it has no date, so it is not repeated.
    val shown = if (details.summary.subtitle == record.value.text("short")) fields - "short" else fields
    ArticleTheme {
        shown.forEachIndexed { index, field ->
            if (index > 0) SectionHeader(fieldLabel(field)) else Spacer(Modifier.size(LocalSpacing.current.md))
            val markdown = record.value.text(field)
            Markdown(
                content = remember(markdown, dataset, baseUrl) { CampaignMarkdown.prepare(markdown, dataset, baseUrl) },
                imageTransformer = Coil3ImageTransformerImpl,
            )
        }
    }
}

@Composable
private fun QuestionRow(question: Question) {
    val spacing = LocalSpacing.current
    val answered = question.answer.isNotEmpty()
    Row(Modifier.padding(vertical = spacing.sm), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Icon(
            if (answered) Icons.Filled.CheckCircle else Icons.Outlined.HelpOutline,
            contentDescription = stringResource(if (answered) R.string.record_question_answered else R.string.record_question_open),
            tint = if (answered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(spacing.iconMedium),
        )
        ArticleTheme {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(question.text, style = MaterialTheme.typography.bodyLarge)
                if (answered) {
                    Text(question.answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The map a location's pin is on: its parent's own map, or the world map when [parentId] is null. */
/**
 * A location's own map as a preview that opens the map viewer. A map this
 * session may not load is left out rather than shown as an empty frame.
 */
@Composable
private fun LocalMap(url: String, name: String, onOpen: () -> Unit) {
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) return
    val description = stringResource(R.string.record_map_description, name)
    SectionHeader(stringResource(R.string.record_map))
    AsyncImage(
        model = url,
        contentDescription = description,
        contentScale = ContentScale.Crop,
        onError = { failed = true },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onOpen),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tags(tags: List<String>) {
    val spacing = LocalSpacing.current
    SectionHeader(stringResource(R.string.record_tags))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        tags.forEach { tag ->
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(tag, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs))
            }
        }
    }
}
