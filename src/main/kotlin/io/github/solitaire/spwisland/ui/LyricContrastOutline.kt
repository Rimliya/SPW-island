// SPDX-License-Identifier: GPL-3.0-only
package io.github.solitaire.spwisland.ui

import io.github.solitaire.spwisland.core.LyricOutlineStyle
import java.awt.Graphics2D
import java.awt.Shape

/** Gives bright cover-colored glyphs a dark edge over a translucent island. */
internal object LyricContrastOutline {
    fun draw(g: Graphics2D, shape: Shape, fontSize: Float, x: Double = 0.0, y: Double = 0.0,
             style: LyricOutlineStyle = LyricOutlineStyle.HARD) =
        LyricOutline.draw(g, shape, fontSize, style, x, y)
}
