// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.core

/** Immutable song facts are rebuilt only when a lyric row changes. */
internal class LyricsDocument private constructor(val lines: List<LyricLine>) {
    private data class Key(val start: Long, val end: Long, val text: String)
    private val byKey: Map<Key, LyricLine> by lazy {
        lines.associateBy { Key(it.startMs, it.endMs, it.text) }
    }
    val usesWordTiming: Boolean by lazy { lines.any { it.hasWordTimingEvidence } }

    fun lastStarted(position: Long): LyricLine? {
        var low = 0
        var high = lines.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (lines[middle].startMs <= position) low = middle + 1 else high = middle
        }
        return lines.getOrNull(low - 1)
    }

    fun withUpdates(updates: List<LyricLine>): LyricsDocument {
        if (updates.isEmpty()) return this
        // Host line callbacks arrive in playback order. Appending avoids rebuilding the lookup
        // index and re-sorting the whole document for every callback.
        val last = lines.lastOrNull()
        if (last != null && appendsAfter(last, updates)) return LyricsDocument(lines + updates)
        if (updates.all { byKey[Key(it.startMs, it.endMs, it.text)] == it }) return this
        val merged = LinkedHashMap(byKey)
        for (line in updates) {
            val key = Key(line.startMs, line.endMs, line.text)
            merged.remove(key)
            merged[key] = line
        }
        return LyricsDocument(merged.values.sortedWith(
            compareBy<LyricLine> { it.startMs }.thenBy { it.endMs }))
    }

    private fun appendsAfter(last: LyricLine, updates: List<LyricLine>): Boolean {
        var previous = last
        for (line in updates) {
            if (line.startMs < previous.startMs ||
                (line.startMs == previous.startMs && line.endMs <= previous.endMs)) return false
            previous = line
        }
        return true
    }

    companion object {
        val EMPTY = LyricsDocument(emptyList())
    }
}
