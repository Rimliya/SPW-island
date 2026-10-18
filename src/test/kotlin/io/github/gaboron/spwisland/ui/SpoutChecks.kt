// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import com.sun.jna.Memory
import io.github.gaboron.spwisland.core.*
import java.awt.image.BufferedImage
import io.github.gaboron.spwisland.platform.CoverColorExtractor
import java.awt.image.DataBufferInt
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

/** Deterministic regressions for the native painter and the compatibility raster; no host or OBS required. */
fun main() {
    val directory = File("build/verification").apply { mkdirs() }
    SwingUtilities.invokeAndWait {
        val settings = IslandSettings(spout = SpoutSettings(enabled = true), fontFamily = "Microsoft YaHei")
        val levels = floatArrayOf(.2f, .5f, .8f, .4f)
        val text = "透明歌词与逐字高亮测试"
        val line = LyricLine(0, 10000, text, "Transparent lyrics / 日本語 / 한국어",
            text.mapIndexed { index, c -> Word(index * 600L, (index + 1) * 600L, c.toString()) })
        val snap = PlaybackSnapshot(Track("Spout 测试", "本地验收", "test.wav"), line, 3000, true, PlaybackStatus.READY)

        /** The shipped path: Skia paints straight into the sender's native buffer, read back for assertions. */
        fun native(options: IslandSettings, snapshot: PlaybackSnapshot = snap): IntArray {
            val output = options.spout
            val pixels = IntArray(output.width * output.height)
            val presentation = SpoutPresentation()
            val painter = SkiaSpoutPainter()
            // SpoutOutput resets the painter on every configuration change; it must remain usable.
            painter.reset()
            val memory = Memory(output.width.toLong() * output.height * 4)
            try {
                var advance = presentation.advance(options, snapshot, levels, .1, false)
                repeat(30) { advance = presentation.advance(options, snapshot, levels, .1, false) }
                painter.paint(memory, output.width, output.height, advance)
                memory.read(0, pixels, 0, pixels.size)
                check(pixels.any { (it ushr 24) != 0 }) { "Native painter produced an empty frame" }
            } finally {
                memory.close(); painter.close(); presentation.close()
            }
            write(pixels, output.width, output.height, File(directory, "native-${output.width}-${snapshot.positionMs}.png"))
            return pixels
        }

        /** The fallback path: the same presentation rasterized by Java2D instead of Skia. */
        fun raster(options: IslandSettings, snapshot: PlaybackSnapshot = snap): IntArray {
            val presentation = SpoutPresentation()
            try {
                var advance = presentation.advance(options, snapshot, levels, .1, true)
                repeat(30) { advance = presentation.advance(options, snapshot, levels, .1, true) }
                return advance.pixels!!.copyOf()
            } finally { presentation.close() }
        }

        fun premultiplied(pixels: IntArray) = pixels.all { val alpha = it ushr 24
            ((it ushr 16) and 255) <= alpha && ((it ushr 8) and 255) <= alpha && (it and 255) <= alpha }

        val a = native(settings)
        // Desktop-only switches must not reach the independent output. Word geometry is prepared on a
        // background worker, so the first frames still render through the timed fallback; re-render the
        // pair until both observe the warmed state instead of comparing two rendering paths.
        val hiddenSettings = settings.copy(enabled = false, hidePaused = true, hideFullscreen = true,
            autoHideOnHover = true, clickThrough = true, positionX = 9999, positionY = -9999)
        var shown = a
        var hidden = native(hiddenSettings)
        var attempts = 0
        while (!shown.contentEquals(hidden) && attempts++ < 8) {
            shown = native(settings); hidden = native(hiddenSettings)
        }
        check(shown.contentEquals(hidden)) { "Desktop visibility or position altered output" }
        check(a[0] == 0 && a.last() == 0) { "Canvas must have transparent corners" }
        check(a.any { (it ushr 24) in 1..254 }) { "Missing partial alpha" }
        check(a.any { (it ushr 24) > 200 }) { "Empty drawing" }
        check(premultiplied(a)) { "Pixels are not premultiplied" }
        check(!a.contentEquals(native(settings, snap.copy(positionMs = 6000)))) { "Karaoke did not advance" }
        check(native(settings.copy(hidePaused = true), snap.copy(playing = false)).any { it != 0 }) {
            "Paused desktop policy must not blank the output"
        }
        val nextLine = line.copy(startMs = 10000, endMs = 20000, text = "切换歌词后的画面", words = emptyList())
        check(!a.contentEquals(native(settings, snap.copy(line = nextLine, positionMs = 13000))))
        check(native(settings.copy(experimentalMultiLine = true), snap.copy(lyrics = listOf(line,
            line.copy(startMs = 1000, text = "重叠歌词", words = emptyList())))).any { it != 0 })
        val small = settings.copy(spout = settings.spout.copy(width = 640, height = 256), shape = IslandShape.NOTCH)
        check(native(small).size == 640 * 256)
        check(native(settings.copy(lowPerformance = true)).any { it != 0 })
        val idle = PlaybackSnapshot(null, null, 0, false, PlaybackStatus.IDLE)
        check(native(settings, idle).any { it != 0 })
        // 0.11+/0.12 presentation features must reach the independent output as well.
        val timed = snap.copy(metadata = TrackMetadata(durationMs = 12_000, coverRgb = 0xd04060))
        val plain = native(settings, timed)
        check(!plain.contentEquals(native(settings.copy(backgroundProgress = BackgroundProgressMode.FILL), timed))) {
            "Background progress fill missing from output"
        }
        check(!plain.contentEquals(native(settings.copy(backgroundProgress = BackgroundProgressMode.TOP_LINE), timed))) {
            "Background progress line missing from output"
        }
        check(!plain.contentEquals(native(settings.copy(backgroundCoverColor = true, lyricCoverColor = true), timed))) {
            "Cover colours missing from output"
        }
        check(!plain.contentEquals(native(settings.copy(fixedWidth = true, maxWidth = 1000), timed))) {
            "Fixed width ignored by output"
        }
        // The Java2D raster is only a fallback, but it must keep the same pixel contract.
        val compatibility = raster(settings)
        check(compatibility.size == settings.spout.width * settings.spout.height)
        check(compatibility[0] == 0 && compatibility.last() == 0) { "Compatibility raster must have transparent corners" }
        check(compatibility.any { (it ushr 24) > 200 }) { "Empty compatibility raster" }
        check(premultiplied(compatibility)) { "Compatibility pixels are not premultiplied" }
        SpoutPresentation().apply { advance(settings, snap, FloatArray(4), .1, true); close() }
        check(IslandVisualColors.parseHex("#D04060")?.rgb == 0xFFD04060.toInt())
        check(IslandVisualColors.fiveBands(floatArrayOf(0f, 1f, .5f, 0f)).contentEquals(
            floatArrayOf(0f, .75f, .75f, .375f, 0f)))
        val paletteCover = CoverArtwork(4, 1, intArrayOf(0xffff3020.toInt(), 0xffff3020.toInt(),
            0xff2060ff.toInt(), 0xff2060ff.toInt()))
        check(CoverColorExtractor.palette(paletteCover).size >= 2) { "Cover palette did not preserve multiple families" }
        val materialBase = native(settings.copy(backgroundMaterial = BackgroundMaterial.SOLID), timed)
        for (material in BackgroundMaterial.entries.filter { it != BackgroundMaterial.SOLID }) {
            check(!materialBase.contentEquals(native(settings.copy(backgroundMaterial = material), timed))) {
                "Background material missing from output: $material"
            }
        }
        check(!plain.contentEquals(native(settings.copy(spectrumLayout = SpectrumLayout.BOTTOM,
            spectrumColorMode = SpectrumColorMode.COVER_FLOW), timed))) { "Bottom spectrum missing from output" }
        check(!plain.contentEquals(native(settings.copy(spectrumLayout = SpectrumLayout.RIGHT_FIVE,
            spectrumStyle = SpectrumStyle.POLYLINE), timed))) { "Five-bar polyline missing from output" }
        val dotClock = native(settings.copy(trailingContent = TrailingContent.CLOCK,
            clockStyle = ClockStyle.DOT_MATRIX), timed)
        val pixelClock = native(settings.copy(trailingContent = TrailingContent.CLOCK,
            clockStyle = ClockStyle.PIXEL), timed)
        check(dotClock.any { it != 0 } && !dotClock.contentEquals(pixelClock)) { "Clock styles missing from output" }
        check(!plain.contentEquals(native(settings.copy(leadingContent = LeadingContent.COVER,
            coverVinylRing = true), timed))) { "Cover vinyl ring missing from output" }
        Thread.sleep(200)
        check(Thread.getAllStackTraces().keys.none { it.name == "island-lyrics-preparation" && it.isAlive }) {
            "Closed renderers must stop lyric preparation"
        }
        println("PASS: desktop independence, alpha, premultiplication, karaoke, resize, notch, low performance, idle, " +
            "background progress, cover colours, fixed width, materials, spectrum layouts, clocks, " +
            "compatibility raster, renderer cleanup")
    }
}

/** Writes the frame for manual inspection; the raster is premultiplied BGRA, the same layout Spout2 receives. */
private fun write(pixels: IntArray, width: Int, height: Int, file: File) {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE)
    pixels.copyInto((image.raster.dataBuffer as DataBufferInt).data)
    ImageIO.write(image, "png", file)
}
