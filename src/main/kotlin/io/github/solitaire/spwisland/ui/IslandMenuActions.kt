// SPDX-License-Identifier: GPL-3.0-only
package io.github.solitaire.spwisland.ui

/**
 * Optional host actions contributed by a composition plugin.
 * The island module never depends on the lyrics module directly.
 */
interface IslandMenuActions {
    fun canDisableCurrentLyrics(): Boolean
    fun disableCurrentLyrics(): Boolean
}
