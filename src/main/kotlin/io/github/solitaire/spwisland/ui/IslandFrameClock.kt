// SPDX-License-Identifier: GPL-3.0-only
package io.github.solitaire.spwisland.ui

import io.github.solitaire.spwisland.core.PerformanceProfile
import java.awt.GraphicsDevice

/** Reads the overlay's display cadence occasionally and subtracts the completed frame's work. */
internal class IslandFrameClock(private val nanoTime: () -> Long = System::nanoTime) {
    private var device: GraphicsDevice? = null
    private var nextDisplayCheck = 0L
    private var refreshRate = 60
    private var started = nanoTime()
    private var cadenceStart: Long? = null

    fun frameStarted(now: Long) { started = now }

    /**
     * Rejects scheduler wakeups that arrive ahead of the requested desktop cadence.
     * The cadence anchor advances only for deliveries that actually render a frame, so
     * timer rounding or a shorter Spout deadline cannot make the overlay exceed its cap.
     */
    fun earlyDeliveryDelay(now: Long, interval: Long): Long? {
        val previous = cadenceStart
        if (previous != null) {
            val earliest = previous + interval
            if (now < earliest) return earliest - now
        }
        cadenceStart = now
        return null
    }

    fun interval(display: GraphicsDevice, profile: PerformanceProfile): Long {
        val now = nanoTime()
        if (device !== display || now >= nextDisplayCheck) {
            device = display
            refreshRate = runCatching { display.displayMode.refreshRate }.getOrDefault(0)
                .takeIf { it > 0 } ?: 60
            nextDisplayCheck = now + 2_000_000_000L
        }
        return 1_000_000_000L / minOf(refreshRate, profile.maxFrameRate)
    }

    fun delay(interval: Long): Long = (started + interval - nanoTime()).coerceAtLeast(1_000_000L)
}
