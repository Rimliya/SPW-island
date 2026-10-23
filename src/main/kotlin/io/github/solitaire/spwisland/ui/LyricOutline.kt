// SPDX-License-Identifier: GPL-3.0-only
package io.github.solitaire.spwisland.ui

import io.github.solitaire.spwisland.core.LyricOutlineStyle
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Shape

/** Paints the contrast edge around lyric glyphs; every layer is a plain stroke, never a mask. */
internal object LyricOutline {
    fun draw(g: Graphics2D, shape: Shape, fontSize: Float, style: LyricOutlineStyle,
             x: Double = 0.0, y: Double = 0.0) {
        val copy = g.create() as Graphics2D
        try {
            copy.translate(x, y)
            val edge = (fontSize * .09f).coerceAtLeast(1.5f)
            when (style) {
                LyricOutlineStyle.HARD -> stroke(copy, shape, edge, HARD_ALPHA)
                LyricOutlineStyle.GRADIENT -> {
                    // Outermost first, so alpha thins outwards while the solid edge stays readable.
                    stroke(copy, shape, edge * 3.2f, 38)
                    stroke(copy, shape, edge * 2.1f, 86)
                    stroke(copy, shape, edge, 186)
                }
                LyricOutlineStyle.DISSOLVE -> dissolve(copy, shape, fontSize, edge)
            }
        } finally { copy.dispose() }
    }

    private fun dissolve(g: Graphics2D, shape: Shape, fontSize: Float, edge: Float) {
        // Broad, broken strokes at low alpha read softer than a blur without any filter allocation.
        for (layer in LAYERS.indices.reversed()) {
            val dash = fontSize * (.30f + .12f * layer)
            val gap = fontSize * (.22f + .10f * layer)
            g.stroke = BasicStroke(edge * LAYERS[layer], BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1f, floatArrayOf(dash, gap), fontSize * .37f * layer)
            g.color = Color(0, 0, 0, ALPHAS[layer])
            g.draw(shape)
        }
    }

    private fun stroke(g: Graphics2D, shape: Shape, width: Float, alpha: Int) {
        g.color = Color(0, 0, 0, alpha)
        g.stroke = BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(shape)
    }

    private const val HARD_ALPHA = 200
    private val LAYERS = floatArrayOf(.9f, 1.5f, 2.2f, 3.0f, 3.9f)
    private val ALPHAS = intArrayOf(24, 34, 44, 36, 18)
}
