package io.github.bardesss.wick.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.bardesss.wick.R

@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Newsreader = FontFamily(
    variable(R.font.newsreader_variable, FontWeight.Normal),
    variable(R.font.newsreader_variable, FontWeight.Medium),
    variable(R.font.newsreader_variable, FontWeight.SemiBold),
)

val InstrumentSans = FontFamily(
    variable(R.font.instrument_sans_variable, FontWeight.Normal),
    variable(R.font.instrument_sans_variable, FontWeight.Medium),
    variable(R.font.instrument_sans_variable, FontWeight.SemiBold),
)

object WickText {
    val Title = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 18.sp)
    val Header = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 28.sp)
    val Meta = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.Normal, fontSize = 12.5.sp)
    val SheetBody = TextStyle(
        fontFamily = Newsreader,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 1.6.em,
    )
}

val WickTypography = Typography(
    headlineMedium = WickText.Header,
    titleMedium = WickText.Title,
    bodyLarge = WickText.SheetBody,
    bodyMedium = TextStyle(fontFamily = InstrumentSans, fontSize = 14.sp),
    labelMedium = WickText.Meta,
    labelLarge = WickText.Meta.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
)
