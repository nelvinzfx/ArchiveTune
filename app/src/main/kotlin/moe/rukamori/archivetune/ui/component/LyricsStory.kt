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
import androidx.compose.ui.draw.blur
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
    
    // Fresh random layout per display (like the original web version), so the
    // same lyric never arranges the same way twice. Stable while on screen.
    val storyLines = remember(words) {
        val fullText = words.joinToString("") { it.text }.trim()
        LyricsAnimationSchedule.layoutStory(words, kotlin.random.Random.nextLong(), fullText.length)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = when (textAlign) {
            TextAlign.Start -> androidx.compose.ui.Alignment.Start
            TextAlign.End -> androidx.compose.ui.Alignment.End
            else -> androidx.compose.ui.Alignment.CenterHorizontally
        }
    ) {
        storyLines.forEach { line ->
            val scaleVariant = line.variant
            
            val groupFontSize = when (scaleVariant) {
                0 -> baseFontSize * 0.85f
                1 -> baseFontSize * 1.1f
                2 -> baseFontSize * 1.5f
                else -> baseFontSize * 2.0f
            }

            FlowRow {
                line.words.forEach { word ->
                    val isRevealed = isPast || (isActive && currentPosSec >= word.startTime)

                    val alpha by animateFloatAsState(
                        targetValue = if (isRevealed) 1f else 0f,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                        label = "alpha"
                    )
                    val translateY by animateFloatAsState(
                        targetValue = if (isRevealed) 0f else 15f,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                        label = "translateY"
                    )
                    val blur by animateFloatAsState(
                        targetValue = if (isRevealed) 0f else 6f,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                        label = "blur"
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (isRevealed) 1f else 0.8f,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                        label = "scale"
                    )

                    Text(
                        text = word.text,
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .graphicsLayer {
                                this.translationY = translateY
                                this.scaleX = scale
                                this.scaleY = scale
                            }
                            .blur(blur.dp)
                            .alpha(if (isActive || isPast) alpha else inactiveAlpha),
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
