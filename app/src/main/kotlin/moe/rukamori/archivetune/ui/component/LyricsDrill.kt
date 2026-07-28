package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.lyrics.WordTimestamp
import kotlin.math.max

// ── Drill motion profile ────────────────────────────────────────────────
// Subtle slam: the word starts slightly enlarged above its resting spot,
// gets thrown down into place with a fast deceleration, holds still, then
// fades out with a small downward drift. Motion blur is faked with two
// trailing ghost copies that sample the word's recent trajectory — cheap
// (no RenderEffect) and directional, like real motion blur.

private const val DRILL_ENTRY_END = 0.38f // fraction of word duration spent slamming in
private const val DRILL_EXIT_START = 0.85f // fraction where the fade-out begins
private const val DRILL_START_SCALE = 1.18f // "slightly bigger" at birth
private const val DRILL_SLAM_DISTANCE_EM = 0.85f // slam travel, relative to font size
private const val DRILL_EXIT_DRIFT_EM = 0.18f // small downward drift while fading
private const val DRILL_GHOST_STEP = 0.03f // trail spacing along the slam path
private const val DRILL_GHOST1_ALPHA = 0.22f
private const val DRILL_GHOST2_ALPHA = 0.12f
private const val DRILL_GHOST3_ALPHA = 0.06f

private fun easeOutQuart(t: Float): Float {
    val u = 1f - t.coerceIn(0f, 1f)
    return 1f - u * u * u * u
}

private fun easeOutCubic(t: Float): Float {
    val u = 1f - t.coerceIn(0f, 1f)
    return 1f - u * u * u
}

private fun easeInQuad(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return c * c
}

/** Vertical offset of the word at entry progress [s], in slam-distance units. */
private fun drillSlamOffset(s: Float): Float = -(1f - easeOutQuart(s))

private class DrillMotion(
    val alpha: Float,
    val scale: Float,
    val translateY: Dp,
    val ghost1TranslateY: Dp,
    val ghost1Alpha: Float,
    val ghost2TranslateY: Dp,
    val ghost2Alpha: Float,
    val ghost3TranslateY: Dp,
    val ghost3Alpha: Float,
)

private fun drillMotion(progress: Float, fontSizeDp: Float): DrillMotion {
    val p = progress.coerceIn(0f, 1f)
    val slamDp = fontSizeDp * DRILL_SLAM_DISTANCE_EM

    return when {
        p < DRILL_ENTRY_END -> {
            val s = p / DRILL_ENTRY_END
            val y = drillSlamOffset(s) * slamDp
            val scale = DRILL_START_SCALE + (1f - DRILL_START_SCALE) * easeOutCubic(s)
            val alpha = easeOutCubic((s * 1.6f).coerceAtMost(1f))

            // Trail strength follows slam velocity (easeOutQuart derivative ~ (1-s)^3)
            // and is capped by the word's own alpha so ghosts never show alone.
            val u = 1f - s
            val velocity = u * u * u
            val trail = velocity * alpha
            val g1y = drillSlamOffset((s - DRILL_GHOST_STEP).coerceAtLeast(0f)) * slamDp
            val g2y = drillSlamOffset((s - 2f * DRILL_GHOST_STEP).coerceAtLeast(0f)) * slamDp
            val g3y = drillSlamOffset((s - 3f * DRILL_GHOST_STEP).coerceAtLeast(0f)) * slamDp

            DrillMotion(
                alpha = alpha,
                scale = scale,
                translateY = y.dp,
                ghost1TranslateY = g1y.dp,
                ghost1Alpha = DRILL_GHOST1_ALPHA * trail,
                ghost2TranslateY = g2y.dp,
                ghost2Alpha = DRILL_GHOST2_ALPHA * trail,
                ghost3TranslateY = g3y.dp,
                ghost3Alpha = DRILL_GHOST3_ALPHA * trail,
            )
        }

        p < DRILL_EXIT_START -> {
            DrillMotion(
                alpha = 1f,
                scale = 1f,
                translateY = 0.dp,
                ghost1TranslateY = 0.dp,
                ghost1Alpha = 0f,
                ghost2TranslateY = 0.dp,
                ghost2Alpha = 0f,
                ghost3TranslateY = 0.dp,
                ghost3Alpha = 0f,
            )
        }

        else -> {
            val t = (p - DRILL_EXIT_START) / (1f - DRILL_EXIT_START)
            val e = easeInQuad(t)
            DrillMotion(
                alpha = 1f - e,
                scale = 1f,
                translateY = (fontSizeDp * DRILL_EXIT_DRIFT_EM * e).dp,
                ghost1TranslateY = 0.dp,
                ghost1Alpha = 0f,
                ghost2TranslateY = 0.dp,
                ghost2Alpha = 0f,
                ghost3TranslateY = 0.dp,
                ghost3Alpha = 0f,
            )
        }
    }
}

private fun drillWordFontMultiplier(length: Int): Float = when {
    length > 10 -> 1.0f
    length > 5 -> 1.2f
    else -> 1.4f
}

@Composable
private fun DrillWordText(
    text: String,
    fontSize: Float,
    isBackgroundWord: Boolean,
    textColor: Color,
    lyricsFontFamily: FontFamily?,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = fontSize.sp,
        fontWeight = lyricsDrillFontWeight(isPunching = false),
        fontStyle = if (isBackgroundWord) FontStyle.Italic else FontStyle.Normal,
        letterSpacing = (-0.03).em,
        fontFamily = lyricsFontFamily,
        style = LocalTextStyle.current.copy(textMotion = TextMotion.Animated),
        color = textColor,
    )
}

@Composable
private fun DrillWord(
    text: String,
    progress: Float,
    isBackgroundWord: Boolean,
    baseFontSize: Float,
    textColor: Color,
    lyricsFontFamily: FontFamily?,
) {
    val multiplier = drillWordFontMultiplier(text.length)
    val wordFontSize = if (isBackgroundWord) baseFontSize * multiplier * 0.82f
    else baseFontSize * multiplier

    val density = LocalDensity.current
    val fontSizeDp = with(density) { wordFontSize.sp.toDp().value }
    val motion = drillMotion(progress, fontSizeDp)
    val bgAlpha = if (isBackgroundWord) 0.82f else 1f

    Box(contentAlignment = Alignment.Center) {
        if (motion.ghost3Alpha > 0.01f) {
            DrillWordText(
                text = text,
                fontSize = wordFontSize,
                isBackgroundWord = isBackgroundWord,
                textColor = textColor,
                lyricsFontFamily = lyricsFontFamily,
                modifier = Modifier.graphicsLayer {
                    translationY = motion.ghost3TranslateY.toPx()
                    scaleX = motion.scale
                    scaleY = motion.scale
                    alpha = motion.ghost3Alpha * bgAlpha
                },
            )
        }
        if (motion.ghost2Alpha > 0.01f) {
            DrillWordText(
                text = text,
                fontSize = wordFontSize,
                isBackgroundWord = isBackgroundWord,
                textColor = textColor,
                lyricsFontFamily = lyricsFontFamily,
                modifier = Modifier.graphicsLayer {
                    translationY = motion.ghost2TranslateY.toPx()
                    scaleX = motion.scale
                    scaleY = motion.scale
                    alpha = motion.ghost2Alpha * bgAlpha
                },
            )
        }
        if (motion.ghost1Alpha > 0.01f) {
            DrillWordText(
                text = text,
                fontSize = wordFontSize,
                isBackgroundWord = isBackgroundWord,
                textColor = textColor,
                lyricsFontFamily = lyricsFontFamily,
                modifier = Modifier.graphicsLayer {
                    translationY = motion.ghost1TranslateY.toPx()
                    scaleX = motion.scale
                    scaleY = motion.scale
                    alpha = motion.ghost1Alpha * bgAlpha
                },
            )
        }
        DrillWordText(
            text = text,
            fontSize = wordFontSize,
            isBackgroundWord = isBackgroundWord,
            textColor = textColor,
            lyricsFontFamily = lyricsFontFamily,
            modifier = Modifier.graphicsLayer {
                translationY = motion.translateY.toPx()
                scaleX = motion.scale
                scaleY = motion.scale
                alpha = motion.alpha * bgAlpha
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricsDrill(
    words: List<WordTimestamp>,
    isActive: Boolean,
    isPast: Boolean,
    currentPositionMs: Long,
    textColor: Color,
    inactiveAlpha: Float,
    baseFontSize: Float,
    isLineAllBackground: Boolean,
    textAlign: TextAlign,
    lyricsFontFamily: FontFamily?,
) {
    val visibleWords = words.filter { it.text.isNotBlank() && it.text != "\n" }
    if (visibleWords.isEmpty()) return

    // Non-active lines: render a compact static line so the list keeps context.
    if (!isActive) {
        val staticText = visibleWords.joinToString(separator = "") { it.text }.trim()
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = when (textAlign) {
                TextAlign.Start -> Arrangement.Start
                TextAlign.End -> Arrangement.End
                else -> Arrangement.Center
            },
        ) {
            Text(
                text = staticText,
                modifier = Modifier.graphicsLayer { alpha = if (isPast) 1f else inactiveAlpha },
                fontSize = if (isLineAllBackground) (baseFontSize * 0.82f).sp else baseFontSize.sp,
                fontWeight = lyricsDrillFontWeight(isPunching = false),
                fontStyle = if (isLineAllBackground) FontStyle.Italic else FontStyle.Normal,
                letterSpacing = (-0.02).em,
                fontFamily = lyricsFontFamily,
                textAlign = textAlign,
                color = textColor,
            )
        }
        return
    }

    val density = LocalDensity.current
    val slotHeight = with(density) {
        (baseFontSize.sp.toDp() * 3.2f).coerceAtLeast(132.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(slotHeight),
        contentAlignment = Alignment.Center,
    ) {
        visibleWords.forEach { word ->
            val startMs = (word.startTime * 1000.0).toLong()
            val endMs = (word.endTime * 1000.0).toLong()
            if (currentPositionMs < startMs || currentPositionMs >= endMs) return@forEach

            val durationMs = max(endMs - startMs, 1L)
            val progress = ((currentPositionMs - startMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

            DrillWord(
                text = word.text.trim(),
                progress = progress,
                isBackgroundWord = isLineAllBackground || word.isBackground,
                baseFontSize = baseFontSize,
                textColor = textColor,
                lyricsFontFamily = lyricsFontFamily,
            )
        }
    }
}

@Composable
fun ImmersiveLyricsDrill(
    words: List<WordTimestamp>,
    currentPositionMs: Long,
    textColor: Color,
    baseFontSize: Float,
    isLineAllBackground: Boolean,
    lyricsFontFamily: FontFamily?,
) {
    val drillWords = words
        .filter { !it.isBackground && it.text.isNotBlank() && it.text != "\n" }
        .ifEmpty {
            words.filter { it.text.isNotBlank() && it.text != "\n" }
        }
    if (drillWords.isEmpty()) return

    val density = LocalDensity.current
    val slotHeight = with(density) {
        (baseFontSize.sp.toDp() * 4f).coerceAtLeast(112.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(slotHeight),
        contentAlignment = Alignment.Center,
    ) {
        drillWords.forEach { word ->
            val displayText = word.text.trimEnd()
            if (displayText.isBlank()) return@forEach

            val startMs = (word.startTime * 1000.0).toLong()
            val endMs = (word.endTime * 1000.0).toLong()
            if (currentPositionMs < startMs || currentPositionMs >= endMs) return@forEach

            val durationMs = max(endMs - startMs, 1L)
            val progress = ((currentPositionMs - startMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

            DrillWord(
                text = displayText,
                progress = progress,
                isBackgroundWord = isLineAllBackground || word.isBackground,
                baseFontSize = baseFontSize,
                textColor = textColor,
                lyricsFontFamily = lyricsFontFamily,
            )
        }
    }
}
