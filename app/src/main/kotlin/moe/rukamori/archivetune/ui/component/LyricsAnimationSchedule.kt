package moe.rukamori.archivetune.ui.component

import moe.rukamori.archivetune.lyrics.WordTimestamp
import moe.rukamori.archivetune.lyrics.LyricsUtils.isJapanese
import moe.rukamori.archivetune.lyrics.LyricsUtils.isChinese
import moe.rukamori.archivetune.lyrics.LyricsUtils.isKorean

object LyricsAnimationSchedule {

    private fun tokenize(text: String): List<String> {
        val isCjkText = isJapanese(text) || isChinese(text) || isKorean(text)
        return if (isCjkText) {
            val chars = mutableListOf<String>()
            val currentWord = StringBuilder()
            text.forEach { char ->
                if (char.isWhitespace()) {
                    if (currentWord.isNotEmpty()) {
                        chars.add(currentWord.toString())
                        currentWord.clear()
                    }
                    chars.add(char.toString())
                } else if (isJapanese(char.toString()) || isChinese(char.toString()) || isKorean(char.toString())) {
                    if (currentWord.isNotEmpty()) {
                        chars.add(currentWord.toString())
                        currentWord.clear()
                    }
                    chars.add(char.toString())
                } else {
                    currentWord.append(char)
                }
            }
            if (currentWord.isNotEmpty()) {
                chars.add(currentWord.toString())
            }
            val groupedTokens = mutableListOf<String>()
            chars.forEach { c ->
                if (c.isBlank()) {
                    if (groupedTokens.isNotEmpty()) {
                        groupedTokens[groupedTokens.lastIndex] = groupedTokens.last() + c
                    }
                } else {
                    groupedTokens.add(c)
                }
            }
            groupedTokens
        } else {
            val raw = text.split(Regex("\\s+")).filter { it.isNotBlank() }
            raw.mapIndexed { index, s ->
                if (index < raw.lastIndex) "$s " else s
            }
        }
    }

    private fun hasPunctuation(word: String): Boolean = word.any { it in ".,!?;:" }

    // Syllable estimate: singers hold syllables, not characters. Latin texts
    // use vowel clusters; CJK roughly one syllable per character.
    private fun syllableCount(token: String): Int {
        val letters = token.filter { it.isLetter() }
        if (letters.isEmpty()) return 1
        val cjk = letters.any {
            isJapanese(it.toString()) || isChinese(it.toString()) || isKorean(it.toString())
        }
        if (cjk) return letters.length
        var clusters = 0
        var inVowel = false
        for (c in letters.lowercase()) {
            val v = c in "aeiouyáéíóúàèìòùâêîôûäëïöüãõ"
            if (v && !inVowel) clusters++
            inVowel = v
        }
        return clusters.coerceAtLeast(1)
    }

    // Blended weight: 50% character-based + 50% syllable-based so timing tracks
    // the vocal closer without drifting too far from the char-based rhythm.
    private fun tokenWeight(token: String): Double {
        val charWeight = 2.0 + token.length
        val syllableWeight = 2.0 + syllableCount(token) * 3.0
        return (charWeight + syllableWeight) / 2.0 + (if (hasPunctuation(token)) 4.0 else 0.0)
    }

    fun synthesizeDrill(text: String, lineStartSec: Double, lineDurationSec: Double): List<WordTimestamp> {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return emptyList()
        val totalWeight = tokens.sumOf { tokenWeight(it) }
        val result = mutableListOf<WordTimestamp>()
        // Breathing room: small lead-in and inter-word gaps so words don't machine-gun.
        val leadIn = 0.07.coerceAtMost(lineDurationSec * 0.1)
        val perGap = if (tokens.size > 1) 0.05.coerceAtMost((lineDurationSec * 0.15) / (tokens.size - 1)) else 0.0
        val distributable = (lineDurationSec - leadIn - perGap * (tokens.size - 1)).coerceAtLeast(lineDurationSec * 0.5)
        var currentOffset = leadIn
        for (token in tokens) {
            val weight = tokenWeight(token)
            val wordDur = (weight / totalWeight) * distributable
            result.add(WordTimestamp(
                text = token,
                startTime = lineStartSec + currentOffset,
                endTime = lineStartSec + currentOffset + wordDur
            ))
            currentOffset += wordDur + perGap
        }
        return result
    }

    fun synthesizeStory(text: String, lineStartSec: Double, lineDurationSec: Double): List<WordTimestamp> {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return emptyList()
        val totalWeight = tokens.sumOf { tokenWeight(it) }
        val startOffset = if (lineDurationSec >= 1.0) 0.1 else 0.0
        val perGap = if (tokens.size > 1) 0.05.coerceAtMost((lineDurationSec * 0.15) / (tokens.size - 1)) else 0.0
        val revealWindow = (lineDurationSec - startOffset - perGap * (tokens.size - 1)).coerceAtLeast(lineDurationSec * 0.6)
        val result = mutableListOf<WordTimestamp>()
        var currentOffset = startOffset
        for (token in tokens) {
            val weight = tokenWeight(token)
            val wordDur = (weight / totalWeight) * revealWindow
            result.add(WordTimestamp(
                text = token,
                startTime = lineStartSec + currentOffset,
                endTime = lineStartSec + currentOffset + wordDur
            ))
            currentOffset += wordDur + perGap
        }
        return result
    }

    // Kinetic (web port): all words land within 80% of the line duration and
    // the tail is held for reading. No gaps or lead-in, words flow contiguously.
    fun synthesizeKinetic(text: String, lineStartSec: Double, lineDurationSec: Double): List<WordTimestamp> {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return emptyList()
        val totalWeight = tokens.sumOf { tokenWeight(it) }
        val revealWindow = (lineDurationSec * 0.8).coerceAtLeast(0.5.coerceAtMost(lineDurationSec))
        val result = mutableListOf<WordTimestamp>()
        var currentOffset = 0.0
        for (token in tokens) {
            val weight = tokenWeight(token)
            val wordDur = (weight / totalWeight) * revealWindow
            result.add(WordTimestamp(
                text = token,
                startTime = lineStartSec + currentOffset,
                endTime = lineStartSec + currentOffset + wordDur
            ))
            currentOffset += wordDur
        }
        return result
    }

    data class StoryLine(val words: List<WordTimestamp>, val variant: Int)

    // Port of the original web layout algorithm: random 1-3 word lines with a
    // random size variant, never repeating the previous line's variant, and
    // the largest variant reserved for short (1-2 word) lines. Seed comes from
    // the caller so each display can be arranged differently.
    fun layoutStory(words: List<WordTimestamp>, seed: Long, fullTextLength: Int): List<StoryLine> {
        if (words.isEmpty()) return emptyList()
        val maxVariant = when {
            fullTextLength > 80 -> 1
            fullTextLength > 40 -> 2
            else -> 3
        }
        val random = kotlin.random.Random(seed)
        val lines = mutableListOf<StoryLine>()
        var lastVariant = -1
        var i = 0
        while (i < words.size) {
            val lineLength = random.nextInt(1, 4).coerceAtMost(words.size - i)
            var variant = random.nextInt(0, maxVariant + 1)
            if (variant == lastVariant) variant = (variant + 1) % (maxVariant + 1)
            if (variant == 3 && lineLength > 2) variant = 2
            lastVariant = variant
            lines.add(StoryLine(words.subList(i, i + lineLength), variant))
            i += lineLength
        }
        return lines
    }
}
