package com.blstream.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class LyricCue(val startms: Long, val endms: Long, val text: String)

class LyricsVM {
    var cues by mutableStateOf<List<LyricCue>>(emptyList())
        private set
    var currentindex by mutableStateOf(-1) // -1 while between lines
        private set
    var nextindex by mutableStateOf(-1)
        private set
    // Positive = show lines later. For .srt files timed a little early, or phones that report position ahead
    var offsetms by mutableStateOf(0L)
        private set
    private var lastpositionms = 0L

    fun LoadLyrics(srt: String) {
        cues = ParseLyrics(srt)
        currentindex = -1
        nextindex = if (cues.isEmpty()) -1 else 0
    }

    fun Clear() {
        cues = emptyList()
        currentindex = -1
        nextindex = -1
        offsetms = 0
    }

    fun SetOffset(ms: Long) {
        offsetms = ms.coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)
        UpdatePosition(lastpositionms) // Apply straight away, even while paused
    }

    fun UpdatePosition(positionms: Long) {
        lastpositionms = positionms
        val list = cues
        val lyricms = positionms - offsetms
        val last = LastStartedCue(list, lyricms)
        val current = if (last >= 0 && lyricms < list[last].endms) last else -1
        val next = if (last + 1 < list.size) last + 1 else -1
        if (current != currentindex) currentindex = current
        if (next != nextindex) nextindex = next
    }

    companion object {
        const val OFFSET_STEP_MS = 250L
        const val MAX_OFFSET_MS = 30_000L

        private val TIMING = Regex("""(\d{1,3}):(\d{1,2}):(\d{1,2})[,.](\d{1,3})\s*-->\s*(\d{1,3}):(\d{1,2}):(\d{1,2})[,.](\d{1,3})""")
        private val TAGS = Regex("""<[^>]+>|\{\\[^}]*\}""")
        private val INDEX = Regex("""^\d+$""")

        // Lenient on purpose: handles BOMs, CRLF, '.' instead of ',' and missing blank lines.
        // Blocks with bad timings or no text are skipped rather than failing the whole file
        fun ParseLyrics(srt: String): List<LyricCue> {
            val lyrics = mutableListOf<LyricCue>()
            val lines = srt.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n').split("\n")

            var i = 0
            while (i < lines.size) {
                val match = TIMING.find(lines[i])
                i++
                if (match == null) continue // Index lines, blanks and junk

                val text = StringBuilder()
                while (i < lines.size && lines[i].isNotBlank()) {
                    if (TIMING.containsMatchIn(lines[i])) break
                    // Next cue's index with the blank line missing
                    if (INDEX.matches(lines[i].trim()) && i + 1 < lines.size && TIMING.containsMatchIn(lines[i + 1])) break
                    val line = lines[i].replace(TAGS, "").trim()
                    if (line.isNotEmpty()) {
                        if (text.isNotEmpty()) text.append('\n')
                        text.append(line)
                    }
                    i++
                }

                val start = ToMillis(match, 1)
                val end = ToMillis(match, 5)
                if (end > start && text.isNotEmpty()) lyrics.add(LyricCue(start, end, text.toString()))
            }
            return lyrics.sortedBy { it.startms }
        }

        private fun ToMillis(match: MatchResult, first: Int): Long {
            val g = match.groupValues
            val hours = g[first].toLong()
            val minutes = g[first + 1].toLong()
            val seconds = g[first + 2].toLong()
            val millis = g[first + 3].padEnd(3, '0').toLong() // ",5" means 500ms
            return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
        }

        // Index of the last cue that has started by positionms, or -1
        fun LastStartedCue(list: List<LyricCue>, positionms: Long): Int {
            var low = 0
            var high = list.size - 1
            var found = -1
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (list[mid].startms <= positionms) {
                    found = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            return found
        }
    }
}
