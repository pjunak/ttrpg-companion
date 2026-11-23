package eu.junak.ttrpg.companion.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowRgb565
import coil3.toBitmap
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.CampaignMap
import eu.junak.ttrpg.companion.core.model.MapViewport
import eu.junak.ttrpg.companion.core.model.RecordRef
import eu.junak.ttrpg.companion.core.model.labelOrder
import eu.junak.ttrpg.companion.core.model.markerScale
import eu.junak.ttrpg.companion.core.model.stops
import eu.junak.ttrpg.companion.core.model.trail
import eu.junak.ttrpg.companion.core.network.maps.MapImage
import eu.junak.ttrpg.companion.ui.components.CenteredMessage
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlin.math.max
import kotlin.math.roundToInt

/** Where a map view looks, owned by its screen's view model so it survives rotation. */
@Stable
class MapCamera {
    var viewport by mutableStateOf<MapViewport?>(null)

    /** The image [viewport] was made for; another image starts fitted again. */
    internal var image by mutableStateOf<String?>(null)
}

/**
 * A map image the reader can pan, pinch and double-tap, with the campaign's
 * pins on top. A tiled image loads the pyramid level that matches the zoom,
 * over coarser tiles already loaded; an untiled one shows the original.
 */
@Composable
internal fun MapView(
    image: MapImage,
    baseUrl: String,
    map: CampaignMap?,
    camera: MapCamera,
    tiles: MapTileImages,
    showEvents: Boolean,
    focus: String?,
    onOpen: (RecordRef) -> Unit,
    onTilesRefused: () -> Unit,
) {
    when (image) {
        is MapImage.Tiled -> {
            val store = remember(image.mediaPath) { TileStore(tiles) }
            val manifest = image.manifest
            val refused by rememberUpdatedState(onTilesRefused)
            LaunchedEffect(store) {
                store.keepLoading(
                    snapshotFlow { camera.viewport?.takeIf { camera.image == image.mediaPath } }
                        .filterNotNull()
                        .map { viewport -> wantedTiles(manifest, viewport).map { baseUrl + manifest.tilePath(image.mediaPath, it) } }
                        .distinctUntilChanged(),
                    onRefused = { refused() },
                )
            }
            MapSurface(image.mediaPath, manifest.width, manifest.height, map, camera, baseUrl, showEvents, focus, onOpen) { viewport ->
                drawTiles(store, manifest, viewport) { baseUrl + manifest.tilePath(image.mediaPath, it) }
            }
        }
        is MapImage.Original -> {
            val context = LocalContext.current
            // Sharp up to twice the fitted zoom: the window's long side twice over, within the memory cap.
            val window = LocalWindowInfo.current.containerSize
            val screenLongSide = maxOf(window.width, window.height)
            val decodeSize = (screenLongSide * ORIGINAL_DETAIL).coerceAtMost(MAX_ORIGINAL_SIZE)
            val original by produceState<OriginalImage>(OriginalImage.Loading, image.mediaPath, baseUrl, decodeSize) {
                // The screen keeps the decoded image; Coil's memory cache would only hold a second large copy alive.
                val request = ImageRequest.Builder(context).data(baseUrl + image.mediaPath).size(decodeSize)
                    .allowRgb565(true)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build()
                val result = SingletonImageLoader.get(context).execute(request)
                value = if (result is SuccessResult) OriginalImage.Ready(result.image.toBitmap().asImageBitmap()) else OriginalImage.Failed
            }
            when (val loaded = original) {
                OriginalImage.Loading -> CenteredMessage(null, loading = true)
                OriginalImage.Failed -> CenteredMessage(stringResource(R.string.map_image_failed))
                is OriginalImage.Ready -> MapSurface(
                    image.mediaPath, loaded.bitmap.width, loaded.bitmap.height, map, camera, baseUrl, showEvents, focus, onOpen,
                ) { viewport ->
                    drawImage(
                        loaded.bitmap,
                        dstOffset = IntOffset(viewport.offsetX.roundToInt(), viewport.offsetY.roundToInt()),
                        dstSize = IntSize(
                            (viewport.imageWidth * viewport.scale).roundToInt(),
                            (viewport.imageHeight * viewport.scale).roundToInt(),
                        ),
                        filterQuality = FilterQuality.Low,
                    )
                }
            }
        }
    }
}

private sealed interface OriginalImage {
    data object Loading : OriginalImage
    data object Failed : OriginalImage
    data class Ready(val bitmap: ImageBitmap) : OriginalImage
}

/** The gestures, background, story trail, markers and zoom buttons over an image of the given native size. */
@Composable
private fun MapSurface(
    imageKey: String,
    imageWidth: Int,
    imageHeight: Int,
    map: CampaignMap?,
    camera: MapCamera,
    baseUrl: String,
    showEvents: Boolean,
    focus: String?,
    onOpen: (RecordRef) -> Unit,
    drawBackground: DrawScope.(MapViewport) -> Unit,
) {
    val density = LocalDensity.current
    val spacing = LocalSpacing.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val focusPin = map?.pins?.firstOrNull { it.ref.key == focus }

    LaunchedEffect(size, imageKey, imageWidth, imageHeight) {
        if (size.width <= 0 || size.height <= 0) return@LaunchedEffect
        val current = camera.viewport
        val width = size.width.toDouble()
        val height = size.height.toDouble()
        val next = if (current == null || camera.image != imageKey ||
            current.imageWidth != imageWidth.toDouble() || current.imageHeight != imageHeight.toDouble()
        ) {
            val fitted = MapViewport.fit(imageWidth, imageHeight, width, height, density.density.toDouble())
            // A location opened from its page is centred at native size, as the website's Show does.
            focusPin?.let { fitted.centeredOn(it.x, it.y, max(fitted.scale, density.density.toDouble())) } ?: fitted
        } else {
            current.resized(width, height)
        }
        camera.image = imageKey
        camera.viewport = next
    }

    val trailColor = MaterialTheme.colorScheme.tertiary
    val trailWidth = with(density) { spacing.xs.toPx() }
    val dash = with(density) { floatArrayOf(spacing.md.toPx(), spacing.sm.toPx()) }
    val ratio = map?.markerZoomRatio ?: 0.0
    val markerScale = { camera.viewport?.let { markerScale(it.zoom, ratio).toFloat() } ?: 1f }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val focusPoint = centroid - pan
                    camera.viewport = camera.viewport?.transformed(
                        pan.x.toDouble(), pan.y.toDouble(), zoom.toDouble(), focusPoint.x.toDouble(), focusPoint.y.toDouble(),
                    )
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { point -> camera.zoomBy(DOUBLE_TAP_ZOOM, point) })
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val viewport = camera.viewport?.takeIf { camera.image == imageKey } ?: return@Canvas
            drawBackground(viewport)
            if (showEvents && map != null) drawTrail(map, viewport, trailColor, trailWidth, dash)
        }
        if (map != null) {
            val touch = with(density) { spacing.markerTouch.toPx() }
            val pinSpots = remember(map, density) {
                map.pins.map { MarkerSpot(it.x, it.y, max(touch, with(density) { it.size.dp.toPx() }) / 2) }
            }
            MarkerLayer(camera, pinSpots) {
                map.pins.forEach { pin ->
                    key(pin.ref.key) {
                        PinMarker(
                            pin = pin,
                            baseUrl = baseUrl,
                            focused = pin.ref.key == focus,
                            scale = markerScale,
                            onClick = { onOpen(pin.ref) },
                        )
                    }
                }
            }
            val labelOrder = remember(map, focus) { labelOrder(map.pins, focus) }
            LabelLayer(camera, map.pins, labelOrder, focus, touch, markerScale)
            if (showEvents) {
                val stops = remember(map) { map.events.stops() }
                MarkerLayer(camera, stops.map { MarkerSpot(it.x, it.y, touch / 2) }) {
                    stops.forEach { stop -> EventStopMarker(stop, onOpen) }
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            SmallFloatingActionButton(onClick = { camera.zoomBy(BUTTON_ZOOM, centre) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.map_zoom_in))
            }
            SmallFloatingActionButton(onClick = { camera.zoomBy(1 / BUTTON_ZOOM, centre) }) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.map_zoom_out))
            }
            SmallFloatingActionButton(onClick = { camera.viewport = camera.viewport?.fitted() }) {
                Icon(Icons.Filled.ZoomOutMap, contentDescription = stringResource(R.string.map_fit))
            }
        }
    }
}

private fun MapCamera.zoomBy(factor: Double, point: Offset) {
    viewport = viewport?.transformed(0.0, 0.0, factor, point.x.toDouble(), point.y.toDouble())
}

/** The decoded original's long side never exceeds this: 64 MB in full colour. */
private const val MAX_ORIGINAL_SIZE = 4096

/** How many times the screen's long side an original is decoded at, so zooming in to twice the fit stays sharp. */
private const val ORIGINAL_DETAIL = 2
private const val DOUBLE_TAP_ZOOM = 2.0
private const val BUTTON_ZOOM = 1.6
