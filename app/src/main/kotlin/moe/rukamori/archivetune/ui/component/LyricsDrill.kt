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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.lyrics.WordTimestamp
import kotlin.math.max

private val DRILL_TIMES = floatArrayOf(0f, 0.15f, 0.85f, 1f)
private val DRILL_OPACITY = floatArrayOf(0f, 1f, 1f, 0f)
private val DRILL_SCALE = floatArrayOf(0.8f, 1.2f, 1f, 0.9f)
private val DRILL_TRANSLATE_Y = floatArrayOf(60f, 0f, 0f, -60f)
private val DRILL_BLUR = floatArrayOf(12f, 0f, 0f, 15f)
private val DRILL_ROTATE_X = floatArrayOf(30f, 0f, 0f, -30f)

private fun drillKeyframe(progress: Float, values: FloatArray): Float {
    val p = progress.coerceIn(0f, 1f)
    if (p <= DRILL_TIMES.first()) return values.first()
    if (p >= DRILL_TIMES.last()) return values.last()
    for (i in 0 until DRILL_TIMES.size - 1) {
        if (p <= DRILL_TIMES[i + 1]) {
            val span = (DRILL_TIMES[i + 1] - DRILL_TIMES[i]).coerceAtLeast(1e-4f)
            val seg = (p - DRILL_TIMES[i]) / span
            return values[i] + (values[i + 1] - values[i]) * seg
        }
    }
    return values.last()
}

private fun drillWordFontMultiplier(length: Int): Float = when {
    length > 10 -> 1.0f
    length > 5 -> 1.2f
    else -> 1.4f
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

            val opacity = drillKeyframe(progress, DRILL_OPACITY)
            val scale = drillKeyframe(progress, DRILL_SCALE)
            val translateY = drillKeyframe(progress, DRILL_TRANSLATE_Y)
            val blur = drillKeyframe(progress, DRILL_BLUR)
            val rotateX = drillKeyframe(progress, DRILL_ROTATE_X)
            val isPunching = progress in 0.15f..0.85f

            val isBackgroundWord = isLineAllBackground || word.isBackground
            val displayText = word.text.trim()
            val multiplier = drillWordFontMultiplier(displayText.length)
            val wordFontSize = if (isBackgroundWord) baseFontSize * multiplier * 0.82f
            else baseFontSize * multiplier

            Text(
                text = displayText,
                modifier = Modifier
                    .graphicsLayer {
                        translationY = translateY.dp.toPx()
                        scaleX = scale
                        scaleY = scale
                        rotationX = rotateX
                        cameraDistance = 12f * density.density
                        alpha = opacity * if (isBackgroundWord) 0.82f else 1f
                    }
                    .blur(blur.dp),
                fontSize = wordFontSize.sp,
                fontWeight = lyricsDrillFontWeight(isPunching = isPunching),
                fontStyle = if (isBackgroundWord) FontStyle.Italic else FontStyle.Normal,
                letterSpacing = (-0.03).em,
                fontFamily = lyricsFontFamily,
                style = LocalTextStyle.current.copy(textMotion = TextMotion.Animated),
                color = textColor,
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

            val opacity = drillKeyframe(progress, DRILL_OPACITY)
            val scale = drillKeyframe(progress, DRILL_SCALE)
            val translateY = drillKeyframe(progress, DRILL_TRANSLATE_Y)
            val blur = drillKeyframe(progress, DRILL_BLUR)
            val rotateX = drillKeyframe(progress, DRILL_ROTATE_X)
            val isPunching = progress in 0.15f..0.85f

            val isBackgroundWord = isLineAllBackground || word.isBackground
            val multiplier = drillWordFontMultiplier(displayText.length)
            val wordFontSize = if (isBackgroundWord) baseFontSize * multiplier * 0.82f
            else baseFontSize * multiplier

            Text(
                text = displayText,
                modifier = Modifier
                    .graphicsLayer {
                        translationY = translateY.dp.toPx()
                        scaleX = scale
                        scaleY = scale
                        rotationX = rotateX
                        cameraDistance = 12f * density.density
                        alpha = opacity * if (isBackgroundWord) 0.82f else 1f
                    }
                    .blur(blur.dp),
                fontSize = wordFontSize.sp,
                fontWeight = lyricsDrillFontWeight(isPunching = isPunching),
                fontStyle = if (isBackgroundWord) FontStyle.Italic else FontStyle.Normal,
                letterSpacing = (-0.03).em,
                fontFamily = lyricsFontFamily,
                style = LocalTextStyle.current.copy(textMotion = TextMotion.Animated),
                color = textColor,
            )
        }
    }
}
