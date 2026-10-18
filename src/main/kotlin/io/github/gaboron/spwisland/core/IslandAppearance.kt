// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.core

/** Classic background fill; liquid glass remains a separate surface style. */
enum class BackgroundMaterial(val storageName: String) {
    SOLID("solid"),
    LINEAR_GRADIENT("gradient"),
    FLOWING_LIGHT("flow"),
    COVER_MOSAIC("mosaic");

    companion object {
        fun fromStorage(value: String): BackgroundMaterial =
            entries.firstOrNull { it.storageName == value } ?: SOLID
    }
}

enum class LeadingContent(val storageName: String) {
    COVER("cover"),
    SPECTRUM("spectrum"),
    NONE("none");

    companion object {
        fun fromStorage(value: String): LeadingContent =
            entries.firstOrNull { it.storageName == value } ?: COVER
    }
}

enum class TrailingContent(val storageName: String) {
    STATUS("status"),
    SPECTRUM("spectrum"),
    CLOCK("clock"),
    NONE("none");

    companion object {
        fun fromStorage(value: String): TrailingContent =
            entries.firstOrNull { it.storageName == value } ?: STATUS
    }
}

enum class SpectrumLayout(val storageName: String) {
    NONE("none"),
    RIGHT_BARS("right_bars"),
    RIGHT_FIVE("right_five"),
    BOTTOM("bottom");

    companion object {
        fun fromStorage(value: String): SpectrumLayout =
            entries.firstOrNull { it.storageName == value } ?: RIGHT_BARS
    }
}

enum class SpectrumColorMode(val storageName: String) {
    MANUAL("manual"),
    COVER("cover"),
    VOLUME("volume"),
    COVER_FLOW("cover_flow"),
    MOSAIC("mosaic");

    companion object {
        fun fromStorage(value: String): SpectrumColorMode =
            entries.firstOrNull { it.storageName == value } ?: COVER
    }
}

enum class SpectrumStyle(val storageName: String) {
    SOLID("solid"),
    POLYLINE("polyline");

    companion object {
        fun fromStorage(value: String): SpectrumStyle =
            entries.firstOrNull { it.storageName == value } ?: SOLID
    }
}

enum class ClockStyle(val storageName: String) {
    TEXT("text"),
    SEVEN_SEGMENT("seven"),
    DOT_MATRIX("dot"),
    PIXEL("pixel");

    companion object {
        fun fromStorage(value: String): ClockStyle =
            entries.firstOrNull { it.storageName == value } ?: DOT_MATRIX
    }
}
