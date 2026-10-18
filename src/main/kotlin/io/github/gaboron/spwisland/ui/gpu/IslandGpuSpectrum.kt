// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui.gpu

import io.github.gaboron.spwisland.core.*
import io.github.gaboron.spwisland.ui.IslandPalette
import io.github.gaboron.spwisland.ui.IslandVisualColors
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import kotlin.math.max
import kotlin.math.min

/** Native Skia spectrum renderer; geometry and color selection mirror the Java2D path. */
internal class IslandGpuSpectrum(private val resources: IslandGpuResources) {
    fun drawSlot(canvas: Canvas, settings: IslandSettings, bands: FloatArray, palette: IslandPalette,
                 centerX: Float, centerY: Float, size: Float) {
        val count = if (settings.spectrumLayout == SpectrumLayout.RIGHT_FIVE) 5 else 4
        val values = if (count == 5) IslandVisualColors.fiveBands(bands)
            else IslandVisualColors.resample(bands, count)
        val colors = IslandVisualColors.spectrumColors(settings, palette, values,
            System.currentTimeMillis(), count).map { it.rgb }
        drawBars(canvas, settings, values, colors, centerX - size * .42f, centerY,
            size * .84f, size, size / 32f)
    }

    fun drawBottom(canvas: Canvas, settings: IslandSettings, bands: FloatArray, palette: IslandPalette,
                   width: Float, height: Float) {
        if (settings.spectrumLayout != SpectrumLayout.BOTTOM) return
        val count = 18
        val values = IslandVisualColors.resample(bands, count)
        val colors = IslandVisualColors.spectrumColors(settings, palette, values,
            System.currentTimeMillis(), count).map { it.rgb }
        val maxHeight = min(height * .34f, 28f).coerceAtLeast(5f)
        drawBars(canvas, settings, values, colors, 14f, height - maxHeight / 2 - 3f,
            (width - 28f).coerceAtLeast(1f), maxHeight * 2, 1f, bottom = true)
    }

    private fun drawBars(canvas: Canvas, settings: IslandSettings, values: FloatArray, colors: List<Int>,
                         left: Float, centerY: Float, width: Float, height: Float, scale: Float,
                         bottom: Boolean = false) {
        val count = values.size.coerceAtLeast(1)
        val gap = width / count * .42f
        val barWidth = ((width - gap * (count - 1)) / count).coerceAtLeast(1f)
        val opacity = settings.spectrumOpacity.coerceIn(0, 100) / 100f
        val active = values.map { it.coerceIn(0f, 1f) }
        if (settings.spectrumStyle == SpectrumStyle.POLYLINE) {
            val points = active.mapIndexed { index, value ->
                val x = left + index * (barWidth + gap) + barWidth / 2
                val y = centerY + height / 2 - 2f - value * (height - 4f)
                x to y
            }
            for (index in 0 until points.lastIndex) {
                val point = points[index]
                val next = points[index + 1]
                canvas.drawLine(point.first, point.second, next.first, next.second,
                    resources.stroke(colors[index % colors.size], max(1.2f, 2f * scale), opacity))
            }
            points.forEachIndexed { index, point ->
                val radius = max(1f, 1.8f * scale)
                canvas.drawCircle(point.first, point.second, radius,
                    resources.fill(colors[index % colors.size], opacity))
            }
            return
        }
        for (index in active.indices) {
            val barHeight = max(2f * scale, active[index] * (height - 2f * scale) + 2f * scale)
            val x = left + index * (barWidth + gap)
            val y = if (bottom) centerY + height / 2 - barHeight else centerY - barHeight / 2
            val color = colors[index % colors.size]
            if (settings.spectrumRounded) {
                canvas.drawRRect(RRect.makeXYWH(x, y, barWidth, barHeight, min(barWidth / 2, 3f * scale)),
                    resources.fill(color, opacity))
            } else {
                canvas.drawRect(Rect.makeXYWH(x, y, barWidth, barHeight), resources.fill(color, opacity))
            }
        }
    }
}
