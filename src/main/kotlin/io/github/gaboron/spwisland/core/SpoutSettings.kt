// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.core

/** Windows output configuration; contains no native or UI dependencies. */
data class SpoutSettings(
    val enabled: Boolean = false,
    val name: String = "SPW Lyrics Island",
    val fps: Int = 60,
    val width: Int = 1280,
    val height: Int = 512,
    val adapter: Int = -1
) : java.io.Serializable
