// SPDX-License-Identifier: GPL-3.0-only
package io.github.solitaire.spwisland.ui

import io.github.solitaire.spwisland.core.ClockStyle
import io.github.solitaire.spwisland.core.IslandSettings
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import kotlin.math.min

/** Compact clock renderer for the trailing slot. */
internal object IslandClockPainter {
    fun draw(g: Graphics2D, settings: IslandSettings, centerX: Float, centerY: Float, size: Float, color: Color) {
        val text = IslandClockGlyphs.time(settings.clock24Hour)
        when (settings.clockStyle) {
            ClockStyle.TEXT -> drawText(g, text, centerX, centerY, size, color)
            ClockStyle.SEVEN_SEGMENT -> drawSeven(g, text, centerX, centerY, size, color)
            ClockStyle.DOT_MATRIX -> drawMatrix(g, text, centerX, centerY, size, color, IslandClockGlyphs::dotPattern)
            ClockStyle.PIXEL -> drawMatrix(g, text, centerX, centerY, size, color, IslandClockGlyphs::pixelPattern)
        }
    }

    private fun drawText(g: Graphics2D, text: String, centerX: Float, centerY: Float, size: Float, color: Color) {
        val font = SystemUiFont.derive(Font.PLAIN, (size * .39f).coerceAtLeast(6f))
        val previousFont = g.font
        val previousColor = g.color
        try {
            g.font = font
            val metrics = g.fontMetrics
            g.color = color
            val width = metrics.stringWidth(text)
            g.drawString(text, centerX - width / 2f, centerY + (metrics.ascent - metrics.descent) / 2f)
        } finally {
            g.font = previousFont
            g.color = previousColor
        }
    }

    private fun drawSeven(g: Graphics2D, text: String, centerX: Float, centerY: Float, size: Float, color: Color) {
        val scale = size / 32f
        val digitWidth = 4.2f * scale
        val digitHeight = 7.2f * scale
        val gap = .7f * scale
        val colonWidth = 1.4f * scale
        val total = digitWidth * 4 + gap * 4 + colonWidth
        var x = centerX - total / 2f
        val y = centerY - digitHeight / 2f
        val stroke = g.stroke
        val oldColor = g.color
        try {
            g.stroke = BasicStroke((1.2f * scale).coerceAtLeast(1f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.color = color
            for (character in text) {
                if (character == ':') {
                    val dot = (.8f * scale).coerceAtLeast(.7f)
                    g.fill(Ellipse2D.Float(x + colonWidth / 2 - dot / 2, y + digitHeight * .28f, dot, dot))
                    g.fill(Ellipse2D.Float(x + colonWidth / 2 - dot / 2, y + digitHeight * .64f, dot, dot))
                    x += colonWidth + gap
                    continue
                }
                drawDigit(g, IslandClockGlyphs.sevenSegments(character), x, y, digitWidth, digitHeight, 1.2f * scale)
                x += digitWidth + gap
            }
        } finally {
            g.stroke = stroke
            g.color = oldColor
        }
    }

    private fun drawDigit(g: Graphics2D, bits: Int, x: Float, y: Float, w: Float, h: Float, thickness: Float) {
        val inset = thickness / 2
        val middle = y + h / 2
        val segments = arrayOf(
            Line2D.Float(x + inset, y + inset, x + w - inset, y + inset),
            Line2D.Float(x + w - inset, y + inset, x + w - inset, middle - inset),
            Line2D.Float(x + w - inset, middle + inset, x + w - inset, y + h - inset),
            Line2D.Float(x + inset, y + h - inset, x + w - inset, y + h - inset),
            Line2D.Float(x + inset, middle + inset, x + inset, y + h - inset),
            Line2D.Float(x + inset, y + inset, x + inset, middle - inset),
            Line2D.Float(x + inset, middle, x + w - inset, middle)
        )
        for (index in segments.indices) if ((bits shr (6 - index)) and 1 == 1) g.draw(segments[index])
    }

    private fun drawMatrix(g: Graphics2D, text: String, centerX: Float, centerY: Float, size: Float,
                           color: Color, pattern: (Char) -> List<String>) {
        val columns = text.sumOf { pattern(it).firstOrNull()?.length ?: 0 } + text.length
        val rows = 7
        val cell = min(size * .82f / columns.coerceAtLeast(1), size * .82f / rows)
        val pixel = cell * .76f
        val totalWidth = columns * cell
        var x = centerX - totalWidth / 2f
        val top = centerY - rows * cell / 2f
        val oldColor = g.color
        try {
            g.color = color
            for (character in text) {
                val glyph = pattern(character)
                for (row in glyph.indices) for (column in glyph[row].indices) {
                    if (glyph[row][column] == '1') {
                        g.fillRect((x + column * cell).toInt(), (top + row * cell).toInt(),
                            pixel.toInt().coerceAtLeast(1), pixel.toInt().coerceAtLeast(1))
                    }
                }
                x += (glyph.firstOrNull()?.length ?: 1) * cell + cell
            }
        } finally {
            g.color = oldColor
        }
    }
}
