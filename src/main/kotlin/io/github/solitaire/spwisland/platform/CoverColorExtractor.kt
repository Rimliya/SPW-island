// SPDX-License-Identifier: GPL-3.0-only
package io.github.solitaire.spwisland.platform

import io.github.solitaire.spwisland.core.CoverArtwork
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.LinkedHashMap
import kotlin.math.max
import kotlin.math.sqrt

/** Selects the largest chromatic color families, avoiding black and white unless no alternative exists. */
object CoverColorExtractor {
    private const val HUE_FAMILIES = 12
    private const val SAMPLE_LIMIT = 4096
    private const val CACHE_LIMIT = 8
    private val artworkCache = object : LinkedHashMap<CoverArtwork, List<Int>>(CACHE_LIMIT, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CoverArtwork, List<Int>>) =
            size > CACHE_LIMIT
    }

    fun dominant(image: BufferedImage): Int? = palette(image, 1).firstOrNull()

    /** Returns up to [count] display-ready colors ordered by visual weight. */
    fun palette(image: BufferedImage, count: Int = 5): List<Int> {
        val step = sampleStep(image.width, image.height)
        val samples = ArrayList<Int>(minOf(SAMPLE_LIMIT, image.width * image.height / max(1, step * step)))
        for (y in 0 until image.height step step) for (x in 0 until image.width step step) {
            val value = image.getRGB(x, y)
            if (value ushr 24 >= 128) samples += value
        }
        return palette(samples, count)
    }

    /** Multi-color extraction for the native cover buffer; results are cached by artwork identity. */
    fun palette(artwork: CoverArtwork, count: Int = 5): List<Int> = synchronized(artworkCache) {
        artworkCache[artwork]?.takeIf { it.isNotEmpty() && it.size >= count }?.let { cached ->
            return@synchronized cached.take(count)
        }
        val step = sampleStep(artwork.width, artwork.height)
        val samples = ArrayList<Int>(minOf(SAMPLE_LIMIT, artwork.width * artwork.height / max(1, step * step)))
        for (y in 0 until artwork.height step step) for (x in 0 until artwork.width step step) {
            val value = artwork.argb[y * artwork.width + x]
            if (value ushr 24 >= 128) samples += value
        }
        palette(samples, max(5, count)).also { artworkCache[artwork] = it }.take(count)
    }

    private fun sampleStep(width: Int, height: Int): Int =
        sqrt((width.toLong() * height).toDouble() / SAMPLE_LIMIT).toInt().coerceAtLeast(1)

    private fun palette(samples: List<Int>, requested: Int): List<Int> {
        if (samples.isEmpty() || requested <= 0) return emptyList()
        val families = Array(HUE_FAMILIES) { Accumulator() }
        val neutral = Accumulator()
        val all = Accumulator()
        val hsb = FloatArray(3)
        for (value in samples) {
            val color = Color(value, true)
            all.add(color)
            Color.RGBtoHSB(color.red, color.green, color.blue, hsb)
            val saturation = hsb[1]
            val brightness = hsb[2]
            val nearBlack = brightness < .08f
            val nearWhite = brightness > .92f && saturation < .12f
            if (!nearBlack && !nearWhite) neutral.add(color)
            if (saturation >= .12f && !nearBlack && !nearWhite) {
                val family = ((hsb[0] * HUE_FAMILIES + .5f).toInt() % HUE_FAMILIES)
                families[family].add(color)
            }
        }
        val minimum = max(1, (samples.size * .025f).toInt())
        val ranked = families.filter { it.count >= minimum }.sortedByDescending { it.count }.toMutableList()
        if (ranked.isEmpty() && neutral.count > 0) ranked += neutral
        if (ranked.isEmpty()) ranked += all
        val colors = ranked.take(requested).map { it.displayColor() }.toMutableList()
        // A monochrome cover still benefits from related shades for flow and mosaic surfaces.
        if (colors.isNotEmpty()) while (colors.size < requested) {
            val base = Color(colors[(colors.size - 1).coerceAtLeast(0)])
            val hsbBase = Color.RGBtoHSB(base.red, base.green, base.blue, null)
            val shift = (colors.size * .11f) * if (colors.size % 2 == 0) 1 else -1
            colors += Color.getHSBColor((hsbBase[0] + shift + 1f) % 1f,
                (hsbBase[1] * .9f).coerceIn(.28f, .82f), (hsbBase[2] + .06f).coerceAtMost(1f)).rgb
        }
        return colors.distinct().ifEmpty { listOf(Color.WHITE.rgb) }
    }

    private class Accumulator {
        var count = 0
        private var red = 0L
        private var green = 0L
        private var blue = 0L
        fun add(color: Color) {
            count++
            red += color.red
            green += color.green
            blue += color.blue
        }

        fun displayColor(): Int {
            val color = Color((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
            val hsb = Color.RGBtoHSB(color.red, color.green, color.blue, null)
            val saturation = if (hsb[1] < .22f) .30f else (hsb[1] * 1.08f).coerceAtMost(.88f)
            val brightness = max(.66f, hsb[2]).coerceAtMost(1f)
            return Color.getHSBColor(hsb[0], saturation, brightness).rgb
        }
    }
}
