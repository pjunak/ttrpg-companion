package eu.junak.ttrpg.companion.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import eu.junak.ttrpg.companion.R

/** The website's title face (Cinzel) and reading face (Lora); interface text stays in the system face. */
private fun variable(resource: Int, weight: Int, style: FontStyle = FontStyle.Normal) = Font(
    resource,
    weight = FontWeight(weight),
    style = style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val TitleFont = FontFamily(
    variable(R.font.cinzel_variable, 400),
    variable(R.font.cinzel_variable, 600),
    variable(R.font.cinzel_variable, 700),
)

val ReadingFont = FontFamily(
    variable(R.font.lora_variable, 400),
    variable(R.font.lora_variable, 600),
    variable(R.font.lora_variable, 700),
    variable(R.font.lora_italic_variable, 400, FontStyle.Italic),
    variable(R.font.lora_italic_variable, 700, FontStyle.Italic),
)

private val base = Typography()

private fun TextStyle.titleFace(size: Int, line: Int, weight: Int = 600) = copy(
    fontFamily = TitleFont,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.02.em,
)

/** Headlines and screen titles use Cinzel, as the website's headings do. */
val CompanionTypography = base.copy(
    displaySmall = base.displaySmall.titleFace(32, 40),
    headlineLarge = base.headlineLarge.titleFace(28, 36),
    headlineMedium = base.headlineMedium.titleFace(25, 32),
    headlineSmall = base.headlineSmall.titleFace(22, 28),
    titleLarge = base.titleLarge.titleFace(20, 26),
)

/**
 * Text styles for record articles, as the Markdown renderer reads them: its
 * headings come from display, headline and title styles, and paragraphs from
 * body styles.
 */
val ArticleTypography = CompanionTypography.copy(
    displayLarge = base.displayLarge.titleFace(23, 30, 700),
    displayMedium = base.displayMedium.titleFace(21, 28, 700),
    displaySmall = base.displaySmall.titleFace(19, 26),
    headlineMedium = base.headlineMedium.copy(fontFamily = ReadingFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 26.sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = ReadingFont, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 25.sp),
    titleLarge = base.titleLarge.copy(fontFamily = ReadingFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 25.sp),
    bodyLarge = base.bodyLarge.copy(fontFamily = ReadingFont, fontSize = 17.sp, lineHeight = 27.sp, letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = ReadingFont, fontSize = 15.sp, lineHeight = 23.sp),
)
