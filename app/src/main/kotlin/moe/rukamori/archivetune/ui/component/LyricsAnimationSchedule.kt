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

    // Syllable estimate: singers hold syllables, not characters, so syllable
    // count tracks real word timing much better than raw length. Latin texts
    // use vowel clusters; CJK roughly one syllable per character.
    private fun syllableWeight(token: String): Double {
        val letters = token.filter { it.isLetter() }
        if (letters.isEmpty()) return 1.0
        val cjk = letters.any {
            isJapanese(it.toString()) || isChinese(it.toString()) || isKorean(it.toString())
        }
        if (cjk) return letters.length.toDouble()
        var clusters = 0
        var inVowel = false
        for (c in letters.lowercase()) {
            val v = c in "aeiouyáéíóúàèìòùâêîôûäëïöüãõ"
            if (v && !inVowel) clusters++
            inVowel = v
        }
        return clusters.coerceAtLeast(1).toDouble()
    }

    private data class Schedule(val startSec: Double, val endSec: Double)

    private fun scheduleTokens(
        tokens: List<String>,
        lineStartSec: Double,
        lineDurationSec: Double,
        leadInSec: Double,
    ): List<Schedule> {
        if (tokens.isEmpty()) return emptyList()
        val n = tokens.size
        // Breathing gaps between words, capped so they never eat the line.
        val perGap = if (n > 1) 0.055.coerceAtMost((lineDurationSec * 0.18) / (n - 1)) else 0.0
        val lead = leadInSec.coerceAtMost(lineDurationSec * 0.1)
        val distributable = (lineDurationSec - lead - perGap * (n - 1))
            .coerceAtLeast(lineDurationSec * 0.5)
        val weights = tokens.map {
            0.7 + 1.5 * syllableWeight(it) + (if (hasPunctuation(it)) 0.8 else 0.0)
        }
        val totalWeight = weights.sum()
        val result = mutableListOf<Schedule>()
        var offset = lead
        for (i in tokens.indices) {
            val dur = (weights[i] / totalWeight) * distributable
            result.add(Schedule(lineStartSec + offset, lineStartSec + offset + dur))
            offset += dur + perGap
        }
        return result
    }

    fun synthesizeDrill(text: String, lineStartSec: Double, lineDurationSec: Double): List<WordTimestamp> {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return emptyList()
        val schedules = scheduleTokens(tokens, lineStartSec, lineDurationSec, leadInSec = 0.08)
        return tokens.mapIndexed { index, token ->
            WordTimestamp(
                text = token,
                startTime = schedules[index].startSec,
                endTime = schedules[index].endSec
            )
        }
    }

    fun synthesizeStory(text: String, lineStartSec: Double, lineDurationSec: Double): List<WordTimestamp> {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return emptyList()
        val leadIn = if (lineDurationSec >= 1.0) 0.12 else 0.0
        val schedules = scheduleTokens(tokens, lineStartSec, lineDurationSec, leadInSec = leadIn)
        return tokens.mapIndexed { index, token ->
            WordTimestamp(
                text = token,
                startTime = schedules[index].startSec,
                endTime = schedules[index].endSec
            )
        }
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
