// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.*
import java.awt.image.DataBufferInt
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

/** Deterministic real Java2D rendering regressions, no host or OBS required. */
fun main() {
    val directory = File("build/verification").apply { mkdirs() }
    SwingUtilities.invokeAndWait {
        val settings = IslandSettings(spout = SpoutSettings(enabled = true), fontFamily = "Microsoft YaHei")
        val text = "透明歌词与逐字高亮测试"
        val line = LyricLine(0, 10000, text, "Transparent lyrics / 日本語 / 한국어",
            text.mapIndexed { index, c -> Word(index * 600L, (index + 1) * 600L, c.toString()) })
        val snap = PlaybackSnapshot(Track("Spout 测试", "本地验收", "test.wav"), line, 3000, true, PlaybackStatus.READY)
        fun frame(options: IslandSettings, snapshot: PlaybackSnapshot = snap): IntArray {
            val renderer = SpoutRenderer()
            var image = renderer.render(options, snapshot, floatArrayOf(.2f, .5f, .8f, .4f), .1)
            repeat(30) { image = renderer.render(options, snapshot, floatArrayOf(.2f, .5f, .8f, .4f), .1) }
            ImageIO.write(image, "png", File(directory, "render-${options.spout.width}-${snapshot.positionMs}.png"))
            return (image.raster.dataBuffer as DataBufferInt).data.copyOf().also { renderer.close() }
        }
        val a = frame(settings)
        val hidden = frame(settings.copy(enabled = false, hidePaused = true, hideFullscreen = true,
            autoHideOnHover = true, clickThrough = true, positionX = 9999, positionY = -9999))
        check(a.contentEquals(hidden)) { "Desktop visibility or position altered output" }
        check(a[0] == 0 && a.last() == 0) { "Canvas must have transparent corners" }
        check(a.any { (it ushr 24) in 1..254 }) { "Missing partial alpha" }
        check(a.any { (it ushr 24) > 200 }) { "Empty drawing" }
        check(a.all { val alpha = it ushr 24; ((it ushr 16) and 255) <= alpha &&
            ((it ushr 8) and 255) <= alpha && (it and 255) <= alpha }) { "Pixels are not premultiplied" }
        check(!a.contentEquals(frame(settings, snap.copy(positionMs = 6000)))) { "Karaoke did not advance" }
        check(frame(settings.copy(hidePaused = true), snap.copy(playing = false)).any { it != 0 }) {
            "Paused desktop policy must not blank the output"
        }
        val nextLine = line.copy(startMs = 10000, endMs = 20000, text = "切换歌词后的画面", words = emptyList())
        check(!a.contentEquals(frame(settings, snap.copy(line = nextLine, positionMs = 13000))))
        check(frame(settings.copy(experimentalMultiLine = true), snap.copy(lyrics = listOf(line,
            line.copy(startMs = 1000, text = "重叠歌词", words = emptyList())))).any { it != 0 })
        val small = settings.copy(spout = settings.spout.copy(width = 640, height = 256), notch = true)
        check(frame(small).size == 640 * 256)
        check(frame(settings.copy(lowPerformance = true)).any { it != 0 })
        val idle = PlaybackSnapshot(null, null, 0, false, PlaybackStatus.IDLE)
        check(frame(settings, idle).any { it != 0 })
        // 0.11+/0.12 presentation features must reach the independent output as well.
        val timed = snap.copy(metadata = TrackMetadata(durationMs = 12_000, coverRgb = 0xd04060))
        val plain = frame(settings, timed)
        check(!plain.contentEquals(frame(settings.copy(backgroundProgress = BackgroundProgressMode.FILL), timed))) {
            "Background progress fill missing from output"
        }
        check(!plain.contentEquals(frame(settings.copy(backgroundProgress = BackgroundProgressMode.TOP_LINE), timed))) {
            "Background progress line missing from output"
        }
        check(!plain.contentEquals(frame(settings.copy(backgroundCoverColor = true, lyricCoverColor = true), timed))) {
            "Cover colours missing from output"
        }
        check(!plain.contentEquals(frame(settings.copy(fixedWidth = true, maxWidth = 1000), timed))) {
            "Fixed width ignored by output"
        }
        SpoutRenderer().apply { render(settings, snap, FloatArray(4), .1); close() }
        Thread.sleep(200)
        check(Thread.getAllStackTraces().keys.none { it.name == "island-lyrics-preparation" && it.isAlive }) {
            "Closed renderers must stop lyric preparation"
        }
        println("PASS: desktop independence, alpha, premultiplication, karaoke, resize, notch, low performance, idle, " +
            "background progress, cover colours, fixed width, renderer cleanup")
    }
}
