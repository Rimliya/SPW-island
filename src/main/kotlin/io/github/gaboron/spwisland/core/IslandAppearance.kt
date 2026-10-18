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
    NONE("none");

    companion object {
        fun fromStorage(value: String): TrailingContent =
            entries.firstOrNull { it.storageName == value } ?: STATUS
    }
}
