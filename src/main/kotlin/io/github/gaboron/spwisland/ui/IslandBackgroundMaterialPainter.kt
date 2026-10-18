// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.BackgroundMaterial
import io.github.gaboron.spwisland.core.IslandSettings
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.Ellipse2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.roundToInt

/** Classic background materials. Liquid glass remains a native-renderer-only surface. */
internal object IslandBackgroundMaterialPainter {
    fun paint(g: Graphics2D, shape: Shape, width: Int, height: Int, settings: IslandSettings,
              palette: IslandPalette) {
        val alpha = settings.opacity * 255 / 100
        when (settings.backgroundMaterial) {
            BackgroundMaterial.SOLID -> solid(g, shape, palette, alpha)
            BackgroundMaterial.LINEAR_GRADIENT -> gradient(g, shape, width, height, settings, palette, alpha)
            BackgroundMaterial.FLOWING_LIGHT -> flowing(g, shape, width, height, settings, palette, alpha)
            BackgroundMaterial.COVER_MOSAIC -> mosaic(g, shape, width, height, settings, palette, alpha)
        }
    }

    private fun solid(g: Graphics2D, shape: Shape, palette: IslandPalette, alpha: Int) {
        g.color = IslandVisualColors.withAlpha(palette.background, alpha)
        g.fill(shape)
    }

    private fun gradient(g: Graphics2D, shape: Shape, width: Int, height: Int, settings: IslandSettings,
                         palette: IslandPalette, alpha: Int) {
        val start = IslandVisualColors.parseHex(settings.gradientStartColor)
            ?: IslandVisualColors.shade(palette.background, 1.45)
        val end = IslandVisualColors.parseHex(settings.gradientEndColor)
            ?: palette.spectrum
        val angle = Math.toRadians(settings.gradientAngle.toDouble())
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        val half = (kotlin.math.abs(dx) * width + kotlin.math.abs(dy) * height) / 2f
        val centerX = width / 2f
        val centerY = height / 2f
        val from = Point2D.Float(centerX - dx * half, centerY - dy * half)
        val to = Point2D.Float(centerX + dx * half, centerY + dy * half)
        val paint = LinearGradientPaint(from, to, floatArrayOf(0f, 1f),
            arrayOf(IslandVisualColors.withAlpha(start, alpha), IslandVisualColors.withAlpha(end, alpha)))
        val previous = g.paint
        try {
            g.paint = paint
            g.fill(shape)
        } finally {
            g.paint = previous
        }
    }

    private fun flowing(g: Graphics2D, shape: Shape, width: Int, height: Int, settings: IslandSettings,
                        palette: IslandPalette, alpha: Int) {
        solid(g, shape, palette, alpha)
        val colors = IslandVisualColors.accents(palette)
        val seconds = System.currentTimeMillis() / 1000.0 * settings.flowSpeed / 100.0
        val clip = g.clip
        try {
            g.clip(shape)
            val radius = max(width, height) * .62
            for (index in colors.indices) {
                val phase = index * 1.37
                val cx = width * (.15 + .18 * index) + sin(seconds * .32 + phase) * width * .16
                val cy = height * .5 + cos(seconds * .27 + phase * 1.7) * height * .42
                val color = colors[index % colors.size]
                for (layer in 0 until 7) {
                    val scale = 1.0 - layer * .12
                    val fade = ((layer + 1) * 4.0).roundToInt().coerceAtMost(30)
                    g.color = IslandVisualColors.withAlpha(color, (fade * settings.opacity / 100.0).roundToInt())
                    val r = radius * scale
                    g.fill(Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2))
                }
            }
        } finally {
            g.clip = clip
        }
    }

    private fun mosaic(g: Graphics2D, shape: Shape, width: Int, height: Int, settings: IslandSettings,
                       palette: IslandPalette, alpha: Int) {
        solid(g, shape, palette, alpha)
        val artwork = palette.coverColors.takeIf { it.isNotEmpty() }
        val clip = g.clip
        val composite = g.composite
        try {
            g.clip(shape)
            if (artwork != null) {
                val accents = IslandVisualColors.accents(palette)
                val block = settings.mosaicBlock.coerceIn(4, 64)
                var y = 0
                var row = 0
                while (y < height) {
                    var x = 0
                    var column = 0
                    while (x < width) {
                        val color = accents[(row * 7 + column * 3) % accents.size]
                        g.color = IslandVisualColors.withAlpha(color,
                            (34 * settings.opacity / 100.0).roundToInt())
                        g.fillRect(x, y, block + 1, block + 1)
                        x += block
                        column++
                    }
                    y += block
                    row++
                }
                g.composite = AlphaComposite.SrcOver.derive(settings.opacity / 140f)
                g.color = Color(4, 5, 9)
                g.fill(shape)
            }
        } finally {
            g.composite = composite
            g.clip = clip
        }
    }
}
