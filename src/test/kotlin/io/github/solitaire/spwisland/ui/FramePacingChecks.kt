// SPDX-License-Identifier: GPL-3.0-only
package io.github.solitaire.spwisland.ui

/** Deterministic checks for the desktop frame-rate ceiling. */
internal fun checkFramePacing() {
    val clock = IslandFrameClock { 0L }
    val interval = 8_333_333L

    check(clock.earlyDeliveryDelay(0L, interval) == null)
    check(clock.earlyDeliveryDelay(7_400_000L, interval) == 933_333L)
    check(clock.earlyDeliveryDelay(interval, interval) == null)
    check(clock.earlyDeliveryDelay(15_700_000L, interval) == 966_666L)
    check(clock.earlyDeliveryDelay(interval * 2, interval) == null)
}
