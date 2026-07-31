package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.lyrics.WordTimestamp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricsStory(
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
    val currentPosSec = currentPositionMs / 1000.0

    val seedBase = remember(words) { words.firstOrNull()?.text?.hashCode()?.toLong() ?: 0L }
    val groups = remember(words) { LyricsAnimationSchedule.getStoryGroups(words, seedBase) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = when (textAlign) {
            TextAlign.Start -> androidx.compose.ui.Alignment.Start
            TextAlign.End -> androidx.compose.ui.Alignment.End
            else -> androidx.compose.ui.Alignment.CenterHorizontally
        }
    ) {
        groups.forEachIndexed { index, group ->
            val groupText = group.joinToString(" ") { it.text }
            val scaleVariant = remember(group) { LyricsAnimationSchedule.getStoryScaleVariant(groupText, seedBase + index) }

            val groupFontSize = when (scaleVariant) {
                0 -> baseFontSize * 1.5f
                1 -> baseFontSize * 1.2f
                2 -> baseFontSize * 1.0f
                else -> baseFontSize * 0.8f
            }

            // Reveal the whole group together at its first word's start — one calm
            // motion per group instead of a stutter-step per synthesized word time.
            val groupStartSec = group.minOf { it.startTime }
            val isRevealed = isPast || (isActive && currentPosSec >= groupStartSec)

            val alpha by animateFloatAsState(
                targetValue = if (isRevealed) 1f else 0f,
                animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing),
                label = "alpha"
            )
            val translateY by animateFloatAsState(
                targetValue = if (isRevealed) 0f else 18f,
                animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing),
                label = "translateY"
            )
            val scale by animateFloatAsState(
                targetValue = if (isRevealed) 1f else 0.92f,
                animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing),
                label = "scale"
            )

            FlowRow(
                modifier = Modifier
                    .graphicsLayer {
                        this.translationY = translateY
                        this.scaleX = scale
                        this.scaleY = scale
                    }
                    .alpha(if (isActive || isPast) alpha else inactiveAlpha)
            ) {
                group.forEach { word ->
                    Text(
                        text = word.text,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        fontSize = if (isLineAllBackground) (groupFontSize * 0.82f).sp else groupFontSize.sp,
                        fontWeight = lyricsStoryFontWeight(isActive = isActive),
                        fontStyle = if (isLineAllBackground || word.isBackground) FontStyle.Italic else FontStyle.Normal,
                        letterSpacing = (-0.035).em,
                        fontFamily = lyricsFontFamily,
                        color = textColor
                    )
                }
            }
        }
    }
}
