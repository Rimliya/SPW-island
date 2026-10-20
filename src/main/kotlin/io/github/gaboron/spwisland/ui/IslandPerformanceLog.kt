// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

/** One sink for frame and Spout metrics; the host may not capture plugin stdout. */
internal object IslandPerformanceLog {
    @Volatile var sink: ((String) -> Unit)? = null

    fun log(line: String) {
        val target = sink
        if (target != null) target(line) else println(line)
    }
}
