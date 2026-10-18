// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

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
}
