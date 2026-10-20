// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import java.util.Locale

/** Read-only frame budget counters; logging never changes presentation behavior. */
internal class IslandFrameMetrics {
    private var windowStart = System.nanoTime()
    private var frames = 0L
    private var visibleFrames = 0L
    private var playingFrames = 0L
    private var layoutFrames = 0L
    private var paintFrames = 0L
    private var selectTotalNs = 0L
    private var layoutTotalNs = 0L
    private var paintTotalNs = 0L
    private var selectMaxNs = 0L
    private var layoutMaxNs = 0L
    private var paintMaxNs = 0L

    fun record(selectNs: Long, layoutNs: Long, paintNs: Long, visible: Boolean, playing: Boolean) {
        frames++
        if (visible) visibleFrames++
        if (playing) playingFrames++
        selectTotalNs += selectNs
        selectMaxNs = maxOf(selectMaxNs, selectNs)
        if (layoutNs > 0) {
            layoutFrames++
            layoutTotalNs += layoutNs
            layoutMaxNs = maxOf(layoutMaxNs, layoutNs)
        }
        if (paintNs > 0) {
            paintFrames++
            paintTotalNs += paintNs
            paintMaxNs = maxOf(paintMaxNs, paintNs)
        }
    }

    fun maybeLog(now: Long, context: () -> String = { "" }) {
        if (now - windowStart < 10_000_000_000L) return
        val elapsed = ((now - windowStart) / 1_000_000_000.0).coerceAtLeast(0.001)
        if (frames > 0L) {
            val selectAvg = if (visibleFrames > 0L) selectTotalNs / visibleFrames / 1_000_000.0 else 0.0
            val layoutAvg = if (layoutFrames > 0L) layoutTotalNs / layoutFrames / 1_000_000.0 else 0.0
            val paintAvg = if (paintFrames > 0L) paintTotalNs / paintFrames / 1_000_000.0 else 0.0
            val line = String.format(Locale.ROOT,
                "[SPW Island] fps=%.1f visibleFps=%.1f playing=%.0f%% " +
                    "selectAvg=%.2fms selectMax=%.2fms layoutAvg=%.2fms layoutMax=%.2fms " +
                    "paintAvg=%.2fms paintMax=%.2fms",
                frames / elapsed, visibleFrames / elapsed, playingFrames * 100.0 / frames,
                selectAvg, selectMaxNs / 1_000_000.0, layoutAvg, layoutMaxNs / 1_000_000.0,
                paintAvg, paintMaxNs / 1_000_000.0)
            IslandPerformanceLog.log(line + context())
        }
        windowStart = now
        frames = 0L; visibleFrames = 0L; playingFrames = 0L
        layoutFrames = 0L; paintFrames = 0L
        selectTotalNs = 0L; layoutTotalNs = 0L; paintTotalNs = 0L
        selectMaxNs = 0L; layoutMaxNs = 0L; paintMaxNs = 0L
    }
}
