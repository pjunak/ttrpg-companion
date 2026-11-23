package eu.junak.ttrpg.companion.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import eu.junak.ttrpg.companion.core.model.character.EffectTone
import eu.junak.ttrpg.companion.core.model.character.ValueMark
import eu.junak.ttrpg.companion.ui.theme.LocalMarkColors
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing

/**
 * A value temporary effects change now: its [mark], the value's [name] and
 * the text it [shows][shown], with the [sentence] a screen reader says
 * ("Armor 20, changed for now by Shrine blessing: +1").
 */
data class MarkedValue(val mark: ValueMark, val name: String, val shown: String, val sentence: String)

/** Opens the causes of a marked value; the sheet screen provides it. */
val LocalMarkDetails = staticCompositionLocalOf<(MarkedValue) -> Unit> { {} }

/** The theme's colour for [tone]. */
@Composable
fun toneColor(tone: EffectTone): Color {
    val colors = LocalMarkColors.current
    return when (tone) {
        EffectTone.BONUS -> colors.bonus
        EffectTone.PENALTY -> colors.penalty
        EffectTone.MIXED -> colors.mixed
    }
}

/** This style in a tone's colour with a soft glow of it, as wide on every screen ([eu.junak.ttrpg.companion.ui.theme.Spacing.glow]). */
@Composable
fun TextStyle.glowing(color: Color): TextStyle {
    val blur = with(LocalDensity.current) { LocalSpacing.current.glow.toPx() }
    return copy(color = color, shadow = Shadow(color.copy(alpha = GLOW_ALPHA), blurRadius = blur))
}

private const val GLOW_ALPHA = 0.55f

/** The small marker before a marked number: up for a bonus, down for a penalty, a diamond for mixed. */
@Composable
fun ToneMarker(tone: EffectTone, modifier: Modifier = Modifier) =
    Icon(markerShapes.getValue(tone), contentDescription = null, tint = toneColor(tone), modifier = modifier.size(LocalSpacing.current.marker))

/**
 * A number that shows its tone three ways, never by colour alone: the
 * tone's colour, a soft glow and a marker before it. Tapping it opens its
 * causes; a screen reader hears its sentence. Unmarked, it is plain text.
 * [touch] keeps the tap area at the accessibility minimum; a row that is
 * itself tappable turns it off.
 */
@Composable
fun MarkedText(
    text: String,
    marked: MarkedValue?,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    touch: Boolean = true,
) {
    if (marked == null) {
        Text(text, modifier, color = color, style = style)
        return
    }
    val open = LocalMarkDetails.current
    val tapArea = if (touch) {
        Modifier.minimumInteractiveComponentSize().clip(MaterialTheme.shapes.extraSmall).clickable(role = Role.Button) { open(marked) }
    } else {
        Modifier
    }
    Row(
        modifier.then(tapArea).clearAndSetSemantics {
            contentDescription = marked.sentence
            if (touch) role = Role.Button
        },
        horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToneMarker(marked.mark.tone)
        Text(text, style = style.glowing(toneColor(marked.mark.tone)))
    }
}

private fun shape(name: String, data: List<Pair<Float, Float>>): ImageVector =
    ImageVector.Builder(name, 10.dp, 10.dp, 10f, 10f).apply {
        path(fill = SolidColor(Color.Black)) {
            data.forEachIndexed { index, (x, y) -> if (index == 0) moveTo(x, y) else lineTo(x, y) }
            close()
        }
    }.build()

private val markerShapes = mapOf(
    EffectTone.BONUS to shape("Up", listOf(5f to 1f, 9.5f to 9f, 0.5f to 9f)),
    EffectTone.PENALTY to shape("Down", listOf(5f to 9f, 0.5f to 1f, 9.5f to 1f)),
    EffectTone.MIXED to shape("Mixed", listOf(5f to 0.5f, 9.5f to 5f, 5f to 9.5f, 0.5f to 5f)),
)
