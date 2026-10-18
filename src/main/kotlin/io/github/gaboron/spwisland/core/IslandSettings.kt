// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.core

const val DEFAULT_LYRIC_FONT_FAMILY = "@island-default"


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
    val shape: IslandShape = IslandShape.ROUNDED_RECTANGLE,
    val squircle: Boolean = true,
    val cornerRoundness: Int = 60,
    val heightMode: IslandHeightMode = IslandHeightMode.AUTO,
    val fixedHeight: Int = 58,
    val textAlignment: IslandTextAlignment = IslandTextAlignment.CENTER,
    val outlineStyle: LyricOutlineStyle = LyricOutlineStyle.HARD,
    val lyricCoverColor: Boolean = false,
    val backgroundCoverColor: Boolean = true,
    val backgroundStyle: IslandBackgroundStyle = IslandBackgroundStyle.CLASSIC,
    val backgroundProgress: BackgroundProgressMode = BackgroundProgressMode.OFF,
    val spectrumCoverColor: Boolean = true,
    val fixedWidth: Boolean = false,
    val leadingContent: LeadingContent = LeadingContent.COVER,
    val trailingContent: TrailingContent = TrailingContent.SPECTRUM,
    val backgroundMaterial: BackgroundMaterial = BackgroundMaterial.SOLID,
    val gradientAngle: Int = 135,
    val gradientStartColor: String = "",
    val gradientEndColor: String = "",
    val flowSpeed: Int = 100,
    val mosaicBlock: Int = 16,
    val coverVisible: Boolean = true,
    val coverScale: Int = 100,
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
) : java.io.Serializable {
    /** Notch motion, placement and occlusion still branch on this boolean. */
    val notch: Boolean get() = shape == IslandShape.NOTCH

    /** Either slot reserves the shared side gutter. */
    val showsSides: Boolean
        get() = leadingContent != LeadingContent.NONE || trailingContent != TrailingContent.NONE

    /** Audio capture and spectrum drawing are active while either slot shows the spectrum. */
    val showsSpectrum: Boolean
        get() = leadingContent == LeadingContent.SPECTRUM || trailingContent == TrailingContent.SPECTRUM
}

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
