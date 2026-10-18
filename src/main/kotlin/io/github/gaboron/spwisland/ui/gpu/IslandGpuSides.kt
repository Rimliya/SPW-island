// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui.gpu

import io.github.gaboron.spwisland.core.*
import io.github.gaboron.spwisland.ui.IslandPalette
import org.jetbrains.skia.*
import kotlin.math.sin

internal class IslandGpuSides(private val resources: IslandGpuResources) {
    fun leading(canvas: Canvas, settings: IslandSettings, artwork: CoverArtwork?, bands: FloatArray,
                x: Float, y: Float, size: Float, palette: IslandPalette) {
        when (settings.leadingContent) {
            LeadingContent.COVER -> if (settings.coverVisible)
                cover(canvas, artwork, x, y, size * settings.coverScale / 100f, palette.spectrum.rgb)
            LeadingContent.SPECTRUM -> spectrum(canvas, bands, x, y, size, palette.spectrum.rgb)
            LeadingContent.NONE -> Unit
        }
    }

    fun trailing(canvas: Canvas, settings: IslandSettings, snapshot: PlaybackSnapshot, bands: FloatArray,
                 x: Float, y: Float, size: Float, palette: IslandPalette, animate: Boolean) {
        when (settings.trailingContent) {
            TrailingContent.SPECTRUM -> spectrum(canvas, bands, x, y, size, palette.spectrum.rgb)
            TrailingContent.STATUS -> status(canvas, snapshot, x, y, animate)
            TrailingContent.NONE -> Unit
        }
    }

    private fun spectrum(canvas: Canvas, bands: FloatArray, x: Float, y: Float, size: Float, color: Int) {
        val scale = size / 32f
        val barWidth = 3f * scale
        for (index in 0 until 4) {
            val barHeight = 2f * scale + bands.getOrElse(index) { 0f }.coerceIn(0f, 1f) * (size - 2f * scale)
            canvas.drawRRect(RRect.makeXYWH(x - 11f * scale + index * 6f * scale, y - barHeight / 2f,
                barWidth, barHeight, 3f * scale), resources.fill(color))
        }
    }

    private fun cover(canvas: Canvas, artwork: CoverArtwork?, x: Float, y: Float, size: Float,
                      color: Int) {
        val scale = size / 32f
        val bounds = Rect.makeXYWH(x - size / 2, y - size / 2, size, size)
        val shape = RRect.makeXYWH(bounds.left, bounds.top, size, size, 4.5f * scale)
        if (artwork == null) {
            canvas.drawRRect(shape, resources.fill((color and 0xffffff) or (55 shl 24)))
            canvas.drawCircle(x, y, 7f * scale, resources.stroke((color and 0xffffff) or (180 shl 24), 1f))
            canvas.drawCircle(x, y, 2f * scale, resources.fill((color and 0xffffff) or (180 shl 24)))
        } else {
            val source = minOf(artwork.width, artwork.height).toFloat()
            canvas.scoped {
                canvas.clipRRect(shape, ClipMode.INTERSECT, true)
                canvas.drawImageRect(resources.cover(artwork), Rect.makeXYWH((artwork.width - source) / 2,
                    (artwork.height - source) / 2, source, source), bounds, SamplingMode.MITCHELL,
                    resources.fill(-1), true)
            }
            canvas.drawRRect(shape, resources.stroke(0x23ffffff, 1f))
        }
    }

    private fun status(canvas: Canvas, snapshot: PlaybackSnapshot, x: Float, y: Float, animate: Boolean) {
        if (snapshot.line == null && snapshot.lyrics.isEmpty() && snapshot.playing) {
            for (index in 0..2) {
                val alpha = if (animate) (150 + 90 * sin(snapshot.positionMs / 350.0 - index)).toInt() else 150
                canvas.drawCircle(x - 6 + index * 7, y, 2f, resources.fill((alpha shl 24) or 0xbeccdd))
            }
        } else if (snapshot.playing) {
            canvas.drawCircle(x, y, 3f, resources.fill(0xff738395.toInt()))
        } else {
            canvas.drawRRect(RRect.makeXYWH(x - 6, y - 5, 3f, 10f, 1f), resources.fill(0xff738395.toInt()))
            canvas.drawRRect(RRect.makeXYWH(x, y - 5, 3f, 10f, 1f), resources.fill(0xff738395.toInt()))
        }
    }
}
