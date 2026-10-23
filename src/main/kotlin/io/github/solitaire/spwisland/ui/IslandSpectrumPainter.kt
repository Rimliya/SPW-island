// SPDX-License-Identifier: GPL-3.0-only
package io.github.solitaire.spwisland.ui

import io.github.solitaire.spwisland.core.IslandSettings
import io.github.solitaire.spwisland.core.SpectrumLayout
import io.github.solitaire.spwisland.core.SpectrumStyle
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.RoundRectangle2D
import kotlin.math.max
import kotlin.math.min

/** Java2D spectrum slot and bottom-edge renderer shared by classic and fallback surfaces. */
internal object IslandSpectrumPainter {
    fun drawSlot(g: Graphics2D, settings: IslandSettings, bands: FloatArray, palette: IslandPalette,
                 centerX: Float, centerY: Float, size: Float) {
        val count = if (settings.spectrumLayout == SpectrumLayout.RIGHT_FIVE) 5 else 4
        val values = if (count == 5) IslandVisualColors.fiveBands(bands)
            else IslandVisualColors.resample(bands, count)
        val colors = IslandVisualColors.spectrumColors(settings, palette, values,
            System.currentTimeMillis(), count)
        val scale = size / 32f
        val left = centerX - size * .42f
        val available = size * .84f
        drawBars(g, values, colors, left, centerY, available, size, scale, settings)
    }

    fun drawBottom(g: Graphics2D, settings: IslandSettings, bands: FloatArray, palette: IslandPalette,
                   width: Float, height: Float) {
        if (settings.spectrumLayout != SpectrumLayout.BOTTOM) return
        val count = 18
        val values = IslandVisualColors.resample(bands, count)
        val colors = IslandVisualColors.spectrumColors(settings, palette, values,
            System.currentTimeMillis(), count)
        val inset = 14f
        val available = (width - inset * 2).coerceAtLeast(1f)
        val maxHeight = min(height * .34f, 28f).coerceAtLeast(5f)
        drawBars(g, values, colors, inset, height - maxHeight / 2 - 3f, available,
            maxHeight * 2, 1f, settings, bottom = true)
    }

    private fun drawBars(g: Graphics2D, values: FloatArray, colors: List<Color>, left: Float,
                         centerY: Float, width: Float, height: Float, scale: Float,
                         settings: IslandSettings, bottom: Boolean = false) {
        val count = values.size.coerceAtLeast(1)
        val gap = width / count * .42f
        val barWidth = ((width - gap * (count - 1)) / count).coerceAtLeast(1f)
        val opacity = settings.spectrumOpacity.coerceIn(0, 100)
        val active = values.map { it.coerceIn(0f, 1f) }
        if (settings.spectrumStyle == SpectrumStyle.POLYLINE) {
            val points = active.mapIndexed { index, value ->
                val x = left + index * (barWidth + gap) + barWidth / 2
                val y = if (bottom) centerY + height / 2 - 2f - value * (height - 4f)
                else centerY + height / 2 - 2f - value * (height - 4f)
                x to y
            }
            val stroke = g.stroke
            try {
                g.stroke = BasicStroke((2f * scale).coerceAtLeast(1.2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                points.dropLast(1).forEachIndexed { index, point ->
                    val next = points[index + 1]
                    g.color = IslandVisualColors.withOpacity(colors[index], opacity)
                    g.draw(Line2D.Float(point.first, point.second, next.first, next.second))
                }
                points.forEachIndexed { index, point ->
                    val radius = (1.8f * scale).coerceAtLeast(1f)
                    g.color = IslandVisualColors.withOpacity(colors[index], opacity)
                    g.fill(Ellipse2D.Float(point.first - radius, point.second - radius, radius * 2, radius * 2))
                }
            } finally {
                g.stroke = stroke
            }
            return
        }
        for (index in active.indices) {
            val barHeight = max(2f * scale, active[index] * (height - 2f * scale) + 2f * scale)
            val x = left + index * (barWidth + gap)
            val y = if (bottom) centerY + height / 2 - barHeight else centerY - barHeight / 2
            g.color = IslandVisualColors.withOpacity(colors[index % colors.size], opacity)
            if (settings.spectrumRounded) {
                val corner = min(barWidth / 2, 3f * scale)
                g.fill(RoundRectangle2D.Float(x, y, barWidth, barHeight, corner, corner))
            } else {
                g.fillRect(x.toInt(), y.toInt(), barWidth.toInt().coerceAtLeast(1),
                    barHeight.toInt().coerceAtLeast(1))
            }
        }
    }
}
