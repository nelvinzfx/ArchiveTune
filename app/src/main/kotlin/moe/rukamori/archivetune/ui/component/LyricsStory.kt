package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/** Duration of a single word's reveal, in seconds (matches the web version's 0.4s). */
private const val STORY_REVEAL_SEC = 0.4

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
    revealAnchorSec: Double? = null,
) {
    val currentPosSec = currentPositionMs / 1000.0

    // Fresh random layout per display (like the original web version), so the
    // same lyric never arranges the same way twice. Stable while on screen.
    val storyLines = remember(words) {
        val fullText = words.joinToString("") { it.text }.trim()
        LyricsAnimationSchedule.layoutStory(words, kotlin.random.Random.nextLong(), fullText.length)
    }

    // Reveal choreography is anchored like the web version: the schedule starts
    // when the line appears (caller-provided anchor) or, in the lyrics list,
    // at whichever is later of the line's scheduled start and this composable's
    // first composition. Words are therefore never "already past" their reveal
    // when they show up — fast/rap lines play every word's animation instead
    // of popping in with the transition skipped. Progress is a pure function
    // of playback position, so seeking and pausing behave correctly too.
    val scheduleStartSec = remember(words) { words.minOf { it.startTime } }
    val composedAtSec = remember { currentPositionMs / 1000.0 }
    val anchorSec = revealAnchorSec ?: maxOf(scheduleStartSec, composedAtSec)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = when (textAlign) {
            TextAlign.Start -> androidx.compose.ui.Alignment.Start
            TextAlign.End -> androidx.compose.ui.Alignment.End
            else -> androidx.compose.ui.Alignment.CenterHorizontally
        }
    ) {
        storyLines.forEach { line ->
            val groupFontSize = when (line.variant) {
                0 -> baseFontSize * 0.85f
                1 -> baseFontSize * 1.1f
                2 -> baseFontSize * 1.5f
                else -> baseFontSize * 2.0f
            }

            FlowRow {
                line.words.forEach { word ->
                    val delaySec = word.startTime - scheduleStartSec
                    val revealProgress = when {
                        isPast -> 1f
                        !isActive -> 0f
                        else -> (((currentPosSec - anchorSec) - delaySec) / STORY_REVEAL_SEC)
                            .toFloat()
                            .coerceIn(0f, 1f)
                    }
                    val eased = FastOutSlowInEasing.transform(revealProgress)

                    Text(
                        text = word.text,
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .graphicsLayer {
                                translationY = 15f * (1f - eased)
                                scaleX = 0.8f + 0.2f * eased
                                scaleY = scaleX
                            }
                            .blur((6f * (1f - eased)).dp)
                            .alpha(if (isActive || isPast) eased else inactiveAlpha),
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
