package eu.junak.ttrpg.companion.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import eu.junak.ttrpg.companion.ui.theme.LabelMinSize

/**
 * A short label, such as a glance tile's caption or a bottom bar's tab name,
 * that shrinks (down to [LabelMinSize]) rather than split a word across lines
 * ("INSPIRE-D", "Vlastno-sti") in a narrow place or with a large font.
 * [style] needs a font size in sp; the colour defaults to the content colour.
 */
@Composable
fun WholeWordText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Unspecified,
    maxLines: Int = 1,
) {
    val shown = style.copy(
        color = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } },
        textAlign = if (textAlign == TextAlign.Unspecified) style.textAlign else textAlign,
    )
    BasicText(text, modifier, style = shown, maxLines = maxLines, autoSize = WholeWords(max = style.fontSize, min = LabelMinSize))
}

/** The largest size from [max] down to [min] at which the text fits its lines without splitting a word. */
private data class WholeWords(val max: TextUnit, val min: TextUnit) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        var size = max.value
        while (size > min.value) {
            val layout = performLayout(constraints, text, size.sp)
            if (!layout.hasVisualOverflow && !layout.splitsWord(text.text)) return size.sp
            size -= STEP
        }
        return min
    }
}

private const val STEP = 0.5f

private fun TextLayoutResult.splitsWord(text: String): Boolean = (0 until lineCount - 1).any { line ->
    val end = getLineEnd(line)
    end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()
}
