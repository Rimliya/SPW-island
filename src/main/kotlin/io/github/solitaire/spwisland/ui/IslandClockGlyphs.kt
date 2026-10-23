// SPDX-License-Identifier: GPL-3.0-only
package io.github.solitaire.spwisland.ui

import java.time.LocalTime

/** Compact clock glyphs adapted from the GPL-3.0 SPW Lyrics Island Studio clock tables. */
internal object IslandClockGlyphs {
    private val seven = mapOf(
        '0' to 0b1111110, '1' to 0b0110000, '2' to 0b1101101, '3' to 0b1111001,
        '4' to 0b0110011, '5' to 0b1011011, '6' to 0b1011111, '7' to 0b1110000,
        '8' to 0b1111111, '9' to 0b1111011, '-' to 0b0000001
    )
    private val dot = mapOf(
        '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        '3' to listOf("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
        '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        '5' to listOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        '6' to listOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        '9' to listOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
        ':' to listOf("00000", "00100", "00100", "00000", "00100", "00100", "00000")
    )
    private val pixel = mapOf(
        '0' to listOf("111", "101", "101", "101", "111"),
        '1' to listOf("010", "110", "010", "010", "111"),
        '2' to listOf("111", "001", "111", "100", "111"),
        '3' to listOf("111", "001", "111", "001", "111"),
        '4' to listOf("101", "101", "111", "001", "001"),
        '5' to listOf("111", "100", "111", "001", "111"),
        '6' to listOf("111", "100", "111", "101", "111"),
        '7' to listOf("111", "001", "010", "010", "010"),
        '8' to listOf("111", "101", "111", "101", "111"),
        '9' to listOf("111", "101", "111", "001", "111"),
        ':' to listOf("0", "1", "0", "1", "0")
    )

    fun time(use24Hour: Boolean, now: LocalTime = LocalTime.now()): String {
        val hour = if (use24Hour) now.hour else ((now.hour + 11) % 12) + 1
        return "%02d:%02d".format(hour, now.minute)
    }

    fun sevenSegments(character: Char): Int = seven[character] ?: 0
    fun dotPattern(character: Char): List<String> = dot[character].orEmpty()
    fun pixelPattern(character: Char): List<String> = pixel[character].orEmpty()
}
