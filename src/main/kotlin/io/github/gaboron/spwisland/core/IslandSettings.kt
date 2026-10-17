// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.core

const val DEFAULT_LYRIC_FONT_FAMILY = "@island-default"

enum class SideContent(val showsSpectrum: Boolean, val showsSides: Boolean) {
    COVER_SPECTRUM(true, true),
    SPECTRUM(true, true),
    COVER(false, true),
    NONE(false, false)
}
enum class BackgroundProgressMode { OFF, FILL, TOP_LINE }

enum class IslandBackgroundStyle(val storageName: String) {
    CLASSIC("classic"), LIQUID_GLASS("liquid_glass");

    companion object {
        fun fromStorage(value: String): IslandBackgroundStyle =
            entries.firstOrNull { it.storageName == value } ?: CLASSIC
    }
}

data class IslandSettings(
    val spout: SpoutSettings = SpoutSettings(),
    val enabled: Boolean = true,
    val translation: Boolean = true,
    val karaoke: Boolean = true,
    val forceKaraoke: Boolean = false,
    val experimentalMultiLine: Boolean = false,
    val hidePaused: Boolean = false,
    val hideFullscreen: Boolean = true,
    val clickThrough: Boolean = false,
    val autoHideOnHover: Boolean = false,
    val lowPerformance: Boolean = false,
    val notch: Boolean = false,
    val cornerRoundness: Int = 60,
    val lyricCoverColor: Boolean = false,
    val backgroundCoverColor: Boolean = true,
    val backgroundStyle: IslandBackgroundStyle = IslandBackgroundStyle.CLASSIC,
    val backgroundProgress: BackgroundProgressMode = BackgroundProgressMode.OFF,
    val spectrumCoverColor: Boolean = true,
    val fixedWidth: Boolean = false,
    val sideContent: SideContent = SideContent.COVER_SPECTRUM,
    val fontFamily: String = DEFAULT_LYRIC_FONT_FAMILY,
    val fontWeight: LyricFontWeight = LyricFontWeight.REGULAR,
    val fontSize: Int = 22,
    val maxWidth: Int = 640,
    val opacity: Int = 96,
    val offsetMs: Int = 0,
    val screen: String = "",
    val positionX: Int? = null,
    val positionY: Int? = null,
    val positionAnchor: IslandAnchor? = null,
    val legacyCenterX: Int? = null,
    val legacyTop: Int? = null
) : java.io.Serializable

interface SettingsStore {
    fun read(): IslandSettings
    fun set(key: String, value: Any)
    fun setFont(family: String, weight: LyricFontWeight) {
        set("font_family", family)
        set("font_weight", weight.storageName)
    }
    fun savePosition(screen: String, x: Int, y: Int, anchor: IslandAnchor)
    fun resetPosition()
    fun resetAll()
}
