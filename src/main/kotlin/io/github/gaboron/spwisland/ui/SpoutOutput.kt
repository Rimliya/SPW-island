// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.*
import io.github.gaboron.spwisland.platform.SpoutSender
import java.awt.AlphaComposite
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/** Independent collapsed presentation, with no native window or desktop visibility dependencies. */
internal class SpoutRenderer(report: (Throwable) -> Unit = {}) : AutoCloseable {
    private val panel = IslandPanel(object : PlaybackActions {
        override fun previous() {}; override fun toggle() {}; override fun next() {}
    }, report).apply {
        isDoubleBuffered = false; expanded = false; expansion = 0.0; anchor = IslandAnchor.TOP_CENTER
        controlsAnimating = false; hoverSettled = true
    }
    private var frame: BufferedImage? = null
    private var lastSnapshot: PlaybackSnapshot? = null
    private var lastLines = emptyList<LyricLine>()
    private var width = 280.0
    private var height = 58.0

    fun render(settings: IslandSettings, snapshot: PlaybackSnapshot, levels: FloatArray, dt: Double): BufferedImage {
        check(SwingUtilities.isEventDispatchThread())
        val output = settings.spout
        panel.settings = settings; panel.snapshot = snapshot
        val lines = ActiveLyrics.select(snapshot, settings.experimentalMultiLine)
        if (lines != lastLines || snapshot.track != lastSnapshot?.track) {
            panel.outgoing = lastSnapshot?.takeIf { it.track == snapshot.track }
            panel.transition = 0.0; lastLines = lines
        }
        lastSnapshot = snapshot
        panel.transition = if (settings.performance.animateLayout) (panel.transition + dt / .65).coerceAtMost(1.0) else 1.0
        panel.updateSpectrum(levels, dt)
        // 0.11+: lyric layouts are measured for the visible lines and upcoming lines are warmed off the EDT.
        panel.prepareLyrics(lines)
        val desired = panel.desiredSize(settings.maxWidth, false)
        val factor = if (settings.performance.animateLayout) 1 - exp(-dt * 15) else 1.0
        width += (desired.width - width) * factor; height += (desired.height - height) * factor
        if (abs(width - desired.width) < .01) width = desired.width.toDouble()
        if (abs(height - desired.height) < .01) height = desired.height.toDouble()
        panel.animatedWidth = width; panel.animatedHeight = height
        panel.setSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1))
        // Background progress (fill / edge line) fades in only in the settled collapsed state, as on the desktop.
        panel.updateProgressTransition(dt)
        panel.doLayout()
        if (frame?.width != output.width || frame?.height != output.height) {
            frame?.flush()
            frame = BufferedImage(output.width, output.height, BufferedImage.TYPE_INT_ARGB_PRE)
        }
        val image = frame!!
        val g = image.createGraphics()
        try {
            g.composite = AlphaComposite.Clear; g.fillRect(0, 0, image.width, image.height)
            g.composite = AlphaComposite.SrcOver
            // Stable scale for normal lyrics; exceptionally tall multiline content must still fit.
            val scale = minOf(2.0, (image.width - 32.0) / settings.maxWidth,
                (image.height - 32.0) / maxOf(height, desired.height.toDouble()))
            g.translate((image.width - panel.width * scale) / 2, (image.height - panel.height * scale) / 2)
            g.scale(scale, scale); panel.paint(g)
        } finally { g.dispose() }
        return image
    }

    override fun close() { panel.close(); frame?.flush(); frame = null }
}

internal class SpoutOutput(private val report: (Throwable) -> Unit) : AutoCloseable {
    private val sender = SpoutSender(report)
    private var renderer: SpoutRenderer? = null
    private var configuration: SpoutSettings? = null
    private var nextFrame = 0L
    private var lastFrame = 0L
    private var failed = false
    private var sampleFrames = 0L
    private var sampleStart = 0L
    private var maxRenderNs = 0L
    private val renderSamples = LongArray(1024)

    fun tick(settings: IslandSettings, snapshot: PlaybackSnapshot, levels: FloatArray, now: Long) {
        val wanted = settings.spout.takeIf { it.enabled }
        if (wanted != configuration) {
            configuration = wanted; failed = false; nextFrame = 0; lastFrame = 0
            sender.configure(wanted)
            renderer?.close()
            renderer = if (wanted == null) null else SpoutRenderer(report)
        }
        if (wanted == null || failed || !sender.ready || now < nextFrame) return
        val period = if (settings.lowPerformance) 67_000_000L else 1_000_000_000L / wanted.fps
        nextFrame = if (nextFrame == 0L || now - nextFrame >= period) now + period else nextFrame + period
        val dt = if (lastFrame == 0L) period / 1e9 else ((now - lastFrame) / 1e9).coerceIn(0.0, .1)
        lastFrame = now
        try {
            val start = System.nanoTime()
            val image = renderer!!.render(settings, snapshot, levels, dt)
            sender.offer((image.raster.dataBuffer as DataBufferInt).data)
            val renderNs = System.nanoTime() - start
            maxRenderNs = maxOf(maxRenderNs, renderNs)
            renderSamples[(sampleFrames % renderSamples.size).toInt()] = renderNs
            if (sampleStart == 0L) sampleStart = now
            sampleFrames++
            if (now - sampleStart >= 10_000_000_000L) {
                val samples = renderSamples.copyOf(minOf(sampleFrames.toInt(), renderSamples.size)).sorted()
                println("[SPW Spout] fps=%.1f frames=%d dropped=%d renderMaxMs=%.2f uploadMaxMs=%.2f renderP95Ms=%.2f".format(
                    sampleFrames * 1e9 / (now - sampleStart), sender.sentFrames, sender.droppedFrames,
                    maxRenderNs / 1e6, sender.maxUploadNs / 1e6, samples[(samples.size * .95).toInt().coerceAtMost(samples.lastIndex)] / 1e6))
                sampleFrames = 0; sampleStart = now; maxRenderNs = 0
            }
        } catch (error: Throwable) {
            failed = true; sender.configure(null); renderer?.close(); renderer = null; report(error)
        }
    }

    fun nextDelayMs(): Int? = if (configuration == null || failed || !sender.ready) null else
        ((nextFrame - System.nanoTime()) / 1_000_000).toInt().coerceAtLeast(1)

    override fun close() { sender.close(); renderer?.close(); renderer = null }
}
