package eu.junak.ttrpg.companion.ui.campaign

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.ui.components.SectionHeader

/** The map a location's pin is on: its parent's own map, or the world map when [parentId] is null. */
/**
 * A location's own map as a preview that opens the map viewer. A map this
 * session may not load is left out rather than shown as an empty frame.
 */
@Composable
internal fun LocalMap(url: String, name: String, onOpen: () -> Unit) {
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
