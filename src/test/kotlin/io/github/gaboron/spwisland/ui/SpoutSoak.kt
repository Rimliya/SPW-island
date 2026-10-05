// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.*
import java.util.concurrent.CountDownLatch
import javax.swing.SwingUtilities
import javax.swing.Timer

/** Synthetic playback with real Java2D and Spout rendering; separate from host callback tests. */
object SpoutSoak {
    @JvmStatic fun main(args: Array<String>) {
        val duration = args.firstOrNull()?.toLongOrNull() ?: 660
        val done = CountDownLatch(1)
        var error: Throwable? = null
        SwingUtilities.invokeAndWait {
            val output = SpoutOutput { error = it; it.printStackTrace() }
            val start = System.nanoTime()
            val cover = CoverArtwork(64, 64, IntArray(4096) { i ->
                if ((i / 64 + i % 64) % 16 < 8) 0xff20bbcc.toInt() else 0xff294566.toInt()
            })
            val timer = Timer(1, null)
            timer.addActionListener {
                val now = System.nanoTime()
                val ms = (now - start) / 1_000_000
                val text = if (ms / 8000 % 2 == 0L) "透明歌词测试：桌面隐藏后继续输出" else "Spout2 / OBS — 独立画面与逐字高亮"
                val begin = ms / 8000 * 8000
                val line = LyricLine(begin, begin + 8000, text, "OBS transparent output · 本地渲染验收",
                    text.mapIndexed { index, c -> Word(begin + index * 220, begin + (index + 1) * 220, c.toString()) })
                val settings = IslandSettings(enabled = false, hideFullscreen = true, hidePaused = true,
                    spout = SpoutSettings(true, "SPW Spout Verification", 60, 1280, 512, 0))
                output.tick(settings, PlaybackSnapshot(Track("Spout Soak", "Synthetic playback", ""), line, ms,
                    true, PlaybackStatus.READY, TrackMetadata(duration * 1000, 0x20bbcc, cover)),
                    SyntheticSpectrum.levels(ms), now)
                timer.delay = output.nextDelayMs() ?: 16
                if (ms >= duration * 1000 || error != null) {
                    timer.stop(); output.close(); done.countDown()
                }
            }
            timer.start()
        }
        done.await(); Thread.sleep(500)
        error?.let { throw it }
        println("SOAK COMPLETE durationSeconds=$duration")
    }
}
