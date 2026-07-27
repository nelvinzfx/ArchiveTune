package moe.rukamori.archivetune.ui.component

import androidx.compose.ui.text.font.FontWeight
import java.util.Locale
import moe.rukamori.archivetune.constants.LyricsTextCase

internal fun applyLyricsTextCase(
    text: String,
    lyricsTextCase: LyricsTextCase,
): String = when (lyricsTextCase) {
    LyricsTextCase.ORIGINAL -> text
    LyricsTextCase.LOWERCASE -> text.lowercase(Locale.getDefault())
    LyricsTextCase.UPPERCASE -> text.uppercase(Locale.getDefault())
}

internal fun lyricsPrimaryFontWeight(): FontWeight = FontWeight.ExtraBold

internal fun lyricsSecondaryFontWeight(): FontWeight = FontWeight.Bold

internal fun lyricsTertiaryFontWeight(): FontWeight = FontWeight.Medium

internal fun lyricsBodyFontWeight(): FontWeight = FontWeight.Normal

internal fun lyricsBaseAnimatedFontWeight(): FontWeight = FontWeight.SemiBold

internal fun lyricsLightInactiveFontWeight(): FontWeight = FontWeight.Light

internal fun lyricsDrillFontWeight(@Suppress("UNUSED_PARAMETER") isPunching: Boolean): FontWeight {
    return lyricsPrimaryFontWeight()
}

internal fun lyricsStoryFontWeight(isActive: Boolean): FontWeight {
    return if (isActive) FontWeight.Black else lyricsPrimaryFontWeight()
}

internal fun lyricsWordHighlightFontWeight(): FontWeight = FontWeight.ExtraBold

internal fun lyricsWordPassedFontWeight(): FontWeight = FontWeight.Bold

internal fun lyricsWordInactiveFontWeight(): FontWeight = FontWeight.Medium

internal fun lyricsWordSoftInactiveFontWeight(): FontWeight = FontWeight.Normal
