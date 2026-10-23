// SPDX-License-Identifier: GPL-3.0-only
package io.github.solitaire.spwisland.core

/** Silhouette of the island body. Corner roundness only applies to [ROUNDED_RECTANGLE]. */
enum class IslandShape(val storageName: String) {
    ROUNDED_RECTANGLE("rounded"),
    CAPSULE("capsule"),
    SQUARE("square"),
    NOTCH("notch");

    /** Corner roundness actually drawn; capsule and square ignore the configured slider. */
    fun roundness(configured: Int): Int = when (this) {
        CAPSULE -> 100
        SQUARE -> 0
        else -> configured.coerceIn(0, 100)
    }

    companion object {
        fun fromStorage(value: String): IslandShape = when (value) {
            "square" -> SQUARE
            "capsule" -> CAPSULE
            "notch" -> NOTCH
            // "pill" was the old label for a 60 % rounded rectangle; keep that appearance.
            else -> ROUNDED_RECTANGLE
        }
    }
}

enum class IslandHeightMode(val storageName: String) {
    AUTO("auto"),
    FIXED("fixed");

    companion object {
        fun fromStorage(value: String): IslandHeightMode =
            entries.firstOrNull { it.storageName == value } ?: AUTO
    }
}

enum class IslandTextAlignment(val storageName: String) {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right");

    companion object {
        fun fromStorage(value: String): IslandTextAlignment =
            entries.firstOrNull { it.storageName == value } ?: CENTER
    }
}

/** How the automatic contrast edge around lyric glyphs is painted. */
enum class LyricOutlineStyle(val storageName: String) {
    HARD("hard"),
    GRADIENT("gradient"),
    DISSOLVE("dissolve");

    companion object {
        fun fromStorage(value: String): LyricOutlineStyle =
            entries.firstOrNull { it.storageName == value } ?: HARD
    }
}
