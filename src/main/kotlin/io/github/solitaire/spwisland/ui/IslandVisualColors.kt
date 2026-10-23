// SPDX-License-Identifier: GPL-3.0-only
package io.github.solitaire.spwisland.ui

import io.github.solitaire.spwisland.core.IslandSettings
import io.github.solitaire.spwisland.core.SpectrumColorMode
import java.awt.Color
import kotlin.math.roundToInt

/** Shared color math for Java2D and native Skia surfaces. */
internal object IslandVisualColors {
    fun parseHex(value: String): Color? {
        val text = value.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
        if (text.length != 6 || text.any { it !in "0123456789abcdefABCDEF" }) return null
        return runCatching { Color(text.toInt(16)) }.getOrNull()
    }

    fun withAlpha(color: Color, alpha: Int): Color =
        Color(color.red, color.green, color.blue, alpha.coerceIn(0, 255))

    fun withOpacity(color: Color, opacity: Int): Color =
        withAlpha(color, (color.alpha * opacity.coerceIn(0, 100) / 100f).roundToInt())

    fun argb(color: Color): Int = (color.alpha shl 24) or color.rgb

    fun mix(first: Color, second: Color, amount: Double): Color {
        val t = amount.coerceIn(0.0, 1.0)
        fun channel(a: Int, b: Int) = (a + (b - a) * t).roundToInt().coerceIn(0, 255)
        return Color(channel(first.red, second.red), channel(first.green, second.green),
            channel(first.blue, second.blue), channel(first.alpha, second.alpha))
    }

    fun shade(color: Color, factor: Double): Color {
        fun channel(value: Int) = (value * factor).roundToInt().coerceIn(0, 255)
        return Color(channel(color.red), channel(color.green), channel(color.blue), color.alpha)
    }

    fun accents(palette: IslandPalette): List<Color> {
        if (palette.coverColors.isNotEmpty()) return palette.coverColors
        val spectrumHsb = Color.RGBtoHSB(palette.spectrum.red, palette.spectrum.green, palette.spectrum.blue, null)
        return (0 until 4).map { index ->
            Color.getHSBColor((spectrumHsb[0] + index * .12f) % 1f,
                (spectrumHsb[1] * .85f).coerceAtLeast(.28f),
                (spectrumHsb[2] * (1f - index * .08f)).coerceIn(.45f, 1f))
        }
    }

    fun spectrumColors(settings: IslandSettings, palette: IslandPalette, bands: FloatArray,
                       timeMs: Long, count: Int): List<Color> {
        if (count <= 0) return emptyList()
        val accents = accents(palette)
        val manual = parseHex(settings.spectrumManualColor) ?: palette.spectrum
        return when (settings.spectrumColorMode) {
            SpectrumColorMode.MANUAL -> List(count) { manual }
            SpectrumColorMode.COVER -> List(count) { palette.spectrum }
            SpectrumColorMode.VOLUME -> List(count) { index ->
                val energy = bands.getOrElse(index % bands.size.coerceAtLeast(1)) { 0f }.coerceIn(0f, 1f)
                val hsb = Color.RGBtoHSB(palette.spectrum.red, palette.spectrum.green, palette.spectrum.blue, null)
                Color.getHSBColor((hsb[0] - .10f * energy + 1f) % 1f,
                    (.48f + .38f * energy).coerceAtMost(.94f), (.68f + .30f * energy).coerceAtMost(1f))
            }
            SpectrumColorMode.COVER_FLOW -> List(count) { index ->
                val phase = timeMs * settings.flowSpeed / 100.0 / 1500.0 + index * .19
                sample(accents, phase)
            }
            SpectrumColorMode.MOSAIC -> List(count) { index -> accents[(index * 3 + index / 3) % accents.size] }
        }
    }

    fun fiveBands(bands: FloatArray): FloatArray {
        if (bands.isEmpty()) return FloatArray(5)
        val out = FloatArray(5)
        for (index in out.indices) {
            val position = index * (bands.size - 1).coerceAtLeast(0) / 4f
            val low = position.toInt().coerceIn(0, bands.lastIndex)
            val high = (low + 1).coerceAtMost(bands.lastIndex)
            val fraction = position - low
            out[index] = bands[low] * (1f - fraction) + bands[high] * fraction
        }
        return out
    }

    fun resample(bands: FloatArray, count: Int): FloatArray {
        if (count <= 0) return FloatArray(0)
        if (bands.isEmpty()) return FloatArray(count)
        if (count == 1) return floatArrayOf(bands.average().toFloat())
        val out = FloatArray(count)
        for (index in out.indices) {
            val position = index * (bands.size - 1).toDouble() / (count - 1)
            val low = position.toInt().coerceIn(0, bands.lastIndex)
            val high = (low + 1).coerceAtMost(bands.lastIndex)
            val fraction = position - low
            out[index] = (bands[low] * (1 - fraction) + bands[high] * fraction).toFloat()
        }
        return out
    }

    private fun sample(colors: List<Color>, position: Double): Color {
        if (colors.size == 1) return colors[0]
        val wrapped = ((position % colors.size) + colors.size) % colors.size
        val low = wrapped.toInt().coerceIn(0, colors.lastIndex)
        val high = (low + 1) % colors.size
        return mix(colors[low], colors[high], wrapped - low)
    }
}
