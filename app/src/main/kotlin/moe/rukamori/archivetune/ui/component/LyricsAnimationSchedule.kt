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

    fun synthesizeDrill(text: String, lineStartSec: Double, lineDurationSec: Double): List<WordTimestamp> {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return emptyList()
        val totalWeight = tokens.sumOf { 2.0 + it.length + (if (hasPunctuation(it)) 4.0 else 0.0) }
        val result = mutableListOf<WordTimestamp>()
        var currentOffset = 0.0
        for (token in tokens) {
            val weight = 2.0 + token.length + (if (hasPunctuation(token)) 4.0 else 0.0)
            val wordDur = (weight / totalWeight) * lineDurationSec
            result.add(WordTimestamp(
                text = token,
                startTime = lineStartSec + currentOffset,
                endTime = lineStartSec + currentOffset + wordDur
            ))
            currentOffset += wordDur
        }
        return result
    }

    fun synthesizeStory(text: String, lineStartSec: Double, lineDurationSec: Double): List<WordTimestamp> {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return emptyList()
        val totalWeight = tokens.sumOf { it.length + 2.0 + (if (hasPunctuation(it)) 4.0 else 0.0) }
        val startOffset = if (lineDurationSec >= 1.0) 0.1 else 0.0
        val revealWindow = (lineDurationSec - startOffset).coerceAtLeast(lineDurationSec * 0.85)
        val result = mutableListOf<WordTimestamp>()
        var currentOffset = startOffset
        for (token in tokens) {
            val weight = token.length + 2.0 + (if (hasPunctuation(token)) 4.0 else 0.0)
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

    fun getStoryGroups(tokens: List<WordTimestamp>, seedBase: Long): List<List<WordTimestamp>> {
        val groups = mutableListOf<List<WordTimestamp>>()
        val random = kotlin.random.Random(seedBase)
        var i = 0
        while (i < tokens.size) {
            val groupSize = random.nextInt(1, 4) // 1, 2, or 3
            val end = (i + groupSize).coerceAtMost(tokens.size)
            groups.add(tokens.subList(i, end))
            i = end
        }
        return groups
    }

    fun getStoryScaleVariant(groupContent: String, seed: Long): Int {
        val random = kotlin.random.Random(seed)
        if (groupContent.length > 80) return random.nextInt(0, 2)
        if (groupContent.length > 40) return random.nextInt(0, 3)
        return random.nextInt(0, 4)
    }
}
