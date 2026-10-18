// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui.gpu

import io.github.gaboron.spwisland.core.ClockStyle
import io.github.gaboron.spwisland.core.IslandSettings
import io.github.gaboron.spwisland.ui.IslandClockGlyphs
import io.github.gaboron.spwisland.ui.LyricTypography
import io.github.gaboron.spwisland.ui.SystemUiFont
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Rect
import java.awt.Font
import kotlin.math.min

/** Native compact clock glyphs for the trailing slot. */
internal class IslandGpuClock(private val resources: IslandGpuResources) {
    fun draw(canvas: Canvas, settings: IslandSettings, centerX: Float, centerY: Float,
             size: Float, color: Int) {
        val text = IslandClockGlyphs.time(settings.clock24Hour)
        when (settings.clockStyle) {
            ClockStyle.TEXT -> text(canvas, text, centerX, centerY, size, color)
            ClockStyle.SEVEN_SEGMENT -> seven(canvas, text, centerX, centerY, size, color)
            ClockStyle.DOT_MATRIX -> matrix(canvas, text, centerX, centerY, size, color,
                IslandClockGlyphs::dotPattern)
            ClockStyle.PIXEL -> matrix(canvas, text, centerX, centerY, size, color,
                IslandClockGlyphs::pixelPattern)
        }
    }

    private fun text(canvas: Canvas, value: String, centerX: Float, centerY: Float, size: Float, color: Int) {
        val font = SystemUiFont.derive(Font.PLAIN, (size * .39f).coerceAtLeast(6f))
        val shaped = LyricTypography.shape(value, font)
        val advance = shaped.layout.advance
        val baseline = centerY + (shaped.layout.ascent - shaped.layout.descent) / 2
        canvas.scoped {
            canvas.translate(centerX - advance / 2, baseline)
            canvas.drawPath(resources.path(shaped.outline), resources.fill(color))
        }
    }

    private fun seven(canvas: Canvas, value: String, centerX: Float, centerY: Float, size: Float, color: Int) {
        val scale = size / 32f
        val digitWidth = 4.2f * scale
        val digitHeight = 7.2f * scale
        val gap = .7f * scale
        val colonWidth = 1.4f * scale
        val total = digitWidth * 4 + gap * 4 + colonWidth
        var x = centerX - total / 2
        val y = centerY - digitHeight / 2
        val paint = resources.stroke(color, (1.2f * scale).coerceAtLeast(1f))
        for (character in value) {
            if (character == ':') {
                val dot = (.8f * scale).coerceAtLeast(.7f)
                canvas.drawCircle(x + colonWidth / 2, y + digitHeight * .28f, dot / 2, resources.fill(color))
                canvas.drawCircle(x + colonWidth / 2, y + digitHeight * .64f, dot / 2, resources.fill(color))
                x += colonWidth + gap
                continue
            }
            digit(canvas, IslandClockGlyphs.sevenSegments(character), x, y, digitWidth, digitHeight, paint)
            x += digitWidth + gap
        }
    }

    private fun digit(canvas: Canvas, bits: Int, x: Float, y: Float, w: Float, h: Float,
                      paint: org.jetbrains.skia.Paint) {
        val inset = 1f
        val middle = y + h / 2
        val segments = arrayOf(
            floatArrayOf(x + inset, y + inset, x + w - inset, y + inset),
            floatArrayOf(x + w - inset, y + inset, x + w - inset, middle - inset),
            floatArrayOf(x + w - inset, middle + inset, x + w - inset, y + h - inset),
            floatArrayOf(x + inset, y + h - inset, x + w - inset, y + h - inset),
            floatArrayOf(x + inset, middle + inset, x + inset, y + h - inset),
            floatArrayOf(x + inset, y + inset, x + inset, middle - inset),
            floatArrayOf(x + inset, middle, x + w - inset, middle)
        )
        for (index in segments.indices) if ((bits shr (6 - index)) and 1 == 1) {
            val line = segments[index]
            canvas.drawLine(line[0], line[1], line[2], line[3], paint)
        }
    }

    private fun matrix(canvas: Canvas, value: String, centerX: Float, centerY: Float, size: Float,
                       color: Int, pattern: (Char) -> List<String>) {
        val columns = value.sumOf { pattern(it).firstOrNull()?.length ?: 0 } + value.length
        val rows = 7
        val cell = min(size * .82f / columns.coerceAtLeast(1), size * .82f / rows)
        val pixel = cell * .76f
        var x = centerX - columns * cell / 2
        val top = centerY - rows * cell / 2
        for (character in value) {
            val glyph = pattern(character)
            for (row in glyph.indices) for (column in glyph[row].indices) {
                if (glyph[row][column] == '1') {
                    canvas.drawRect(Rect.makeXYWH(x + column * cell, top + row * cell, pixel, pixel),
                        resources.fill(color))
                }
            }
            x += (glyph.firstOrNull()?.length ?: 1) * cell + cell
        }
    }
}
