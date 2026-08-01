package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.lyrics.WordTimestamp
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

// Word entrance spring ported from the web LyricKinetic (lyrics_gen):
// framer-motion type:"spring", damping 30, stiffness 350, mass 1.
// For those values the damped harmonic oscillator has an exact closed form:
// zeta*omega = damping/(2*mass) = 15, omegaD = sqrt(stiffness - 15^2) = sqrt(125).
// Slightly underdamped, so words land with a subtle overshoot/bounce.
// Because it is a pure function of elapsed time, reveal progress can be driven
// straight from playback position (seek/pause safe), same model as LyricsStory.
private const val KINETIC_SPRING_ENV = 15.0
private const val KINETIC_SPRING_OMEGA_D = 11.180340
private const val KINETIC_SPRING_RATIO = KINETIC_SPRING_ENV / KINETIC_SPRING_OMEGA_D

private fun kineticSpring(tSec: Double): Double {
    if (tSec <= 0.0) return 0.0
    val phase = KINETIC_SPRING_OMEGA_D * tSec
    return 1.0 - exp(-KINETIC_SPRING_ENV * tSec) * (cos(phase) + KINETIC_SPRING_RATIO * sin(phase))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricsKinetic(
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
    modifier: Modifier = Modifier,
    revealAnchorSec: Double? = null,
    lineTextLength: Int = 0,
) {
    // Size tiers from the web version: long lines stay readable, short lines go huge.
    val fontSize = remember(lineTextLength, baseFontSize) {
        when {
            lineTextLength > 50 -> baseFontSize * 1.1f
            lineTextLength > 20 -> baseFontSize * 1.9f
            else -> baseFontSize * 2.3f
        }.sp
    }

    if (words.isEmpty()) {
        // Instrumental-style line: pulsing ellipsis, like the web version.
        val pulse = rememberInfiniteTransition(label = "kineticInstrumental")
        val pulseAlpha by pulse.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
            label = "alpha",
        )
        val pulseScale by pulse.animateFloat(
            initialValue = 1f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
            label = "scale",
        )
        Text(
            text = "...",
            color = textColor.copy(alpha = 0.5f * pulseAlpha),
            fontSize = fontSize,
            fontWeight = FontWeight.Black,
            fontFamily = lyricsFontFamily,
            letterSpacing = 0.2.em,
            modifier =
                modifier.graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                },
        )
        return
    }

    // Same anchor model as LyricsStory: the reveal schedule starts when the
    // line activates (immersive passes an explicit anchor), so fast lines
    // play every word's entrance instead of popping in pre-revealed.
    val scheduleStartSec = remember(words) { words.minOfOrNull { it.startTime } ?: 0.0 }
    val composedAtSec = remember { currentPositionMs / 1000.0 }
    val anchorSec = revealAnchorSec ?: maxOf(scheduleStartSec, composedAtSec)
    val currentPosSec = currentPositionMs / 1000.0

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement =
            if (textAlign == TextAlign.Center) {
                Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
            } else {
                Arrangement.spacedBy(10.dp)
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        words.forEach { word ->
            val delaySec = word.startTime - scheduleStartSec
            val eased =
                when {
                    isPast || !isActive -> 1.0
                    else -> kineticSpring(currentPosSec - anchorSec - delaySec)
                }
            val alpha =
                when {
                    isPast -> 1f
                    !isActive -> inactiveAlpha
                    else -> eased.toFloat().coerceIn(0f, 1f)
                }
            val entranceBlur = if (isActive) ((1.0 - eased).coerceAtLeast(0.0) * 10).dp else 0.dp
            Text(
                text = word.text.trimEnd(),
                color = textColor,
                fontSize = fontSize,
                lineHeight = fontSize * 0.9,
                fontWeight = FontWeight.Black,
                fontFamily = lyricsFontFamily,
                letterSpacing = (-0.04).em,
                style = TextStyle(shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 24f)),
                modifier =
                    Modifier
                        .graphicsLayer {
                            this.alpha = alpha
                            translationY = (1.0 - eased).toFloat() * 24.dp.toPx()
                            scaleX = 1.2f - 0.2f * eased.toFloat()
                            scaleY = scaleX
                        }.blur(entranceBlur),
            )
        }
    }
}
