// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.*
import java.awt.*
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D

/** Paints the configured leading visual without owning artwork decoding or layout. */
internal object IslandLeadingContent {
    fun draw(g: Graphics2D, settings: IslandSettings, cover: CoverArtwork?, bands: FloatArray,
             centerX: Float, centerY: Float, size: Float, palette: IslandPalette) {
        when (settings.leadingContent) {
            LeadingContent.COVER -> if (settings.coverVisible)
                cover(g, cover, centerX, centerY, size * settings.coverScale / 100f, palette.spectrum)
            LeadingContent.SPECTRUM -> if (settings.spectrumLayout != SpectrumLayout.NONE)
                IslandSpectrumPainter.drawSlot(g, settings, bands, palette, centerX, centerY, size)
            LeadingContent.NONE -> Unit
        }
    }

    private fun cover(g: Graphics2D, artwork: CoverArtwork?, centerX: Float, centerY: Float,
                      size: Float, accent: Color) {
        val scale = size / BASE_SIZE
        val x = centerX - size / 2f
        val y = centerY - size / 2f
        val shape = RoundRectangle2D.Float(x, y, size, size, 9f * scale, 9f * scale)
        if (artwork == null) {
            g.color = Color(accent.red, accent.green, accent.blue, 55)
            g.fill(shape)
            g.color = Color(accent.red, accent.green, accent.blue, 180)
            g.draw(Ellipse2D.Float(centerX - 7f * scale, centerY - 7f * scale,
                14f * scale, 14f * scale))
            g.fill(Ellipse2D.Float(centerX - 2f * scale, centerY - 2f * scale,
                4f * scale, 4f * scale))
            return
        }
        val image = IslandArtworkImages.image(artwork)
        val copy = g.create() as Graphics2D
        try {
            copy.clip(shape)
            copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            val source = minOf(image.width, image.height)
            val sx = (image.width - source) / 2f
            val sy = (image.height - source) / 2f
            val imageScale = size / source
            val transform = AffineTransform.getTranslateInstance(
                (x - sx * imageScale).toDouble(), (y - sy * imageScale).toDouble()
            ).apply { scale(imageScale.toDouble(), imageScale.toDouble()) }
            copy.drawImage(image, transform, null)
        } finally { copy.dispose() }
        g.color = Color(255, 255, 255, 35)
        g.draw(shape)
    }

    private const val BASE_SIZE = 32f
}
