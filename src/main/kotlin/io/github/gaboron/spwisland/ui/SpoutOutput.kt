// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import com.sun.jna.Pointer
import io.github.gaboron.spwisland.core.*
import io.github.gaboron.spwisland.platform.SkikoRuntime
import io.github.gaboron.spwisland.platform.SpoutSender
import io.github.gaboron.spwisland.ui.gpu.IslandGpuFrame
import io.github.gaboron.spwisland.ui.gpu.IslandGpuScene
import io.github.gaboron.spwisland.ui.gpu.scoped
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface
import java.awt.AlphaComposite
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/** One presentation step: the native frame plus the portability raster when the fallback asked for it. */
internal class SpoutAdvance(val scene: IslandGpuFrame, val scale: Double, val pixels: IntArray?)

/** Event-thread presentation state: offscreen panel, animation and the Java2D compatibility raster. */
internal class SpoutPresentation(report: (Throwable) -> Unit = {}) : AutoCloseable {
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

    /** Advances one presentation step. Only the Swing event thread may touch the panel. */
    fun advance(settings: IslandSettings, snapshot: PlaybackSnapshot, levels: FloatArray, dt: Double,
                compatibility: Boolean): SpoutAdvance {
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
        // Stable scale for normal lyrics; exceptionally tall multiline content must still fit.
        val scale = minOf(2.0, (output.width - 32.0) / settings.maxWidth,
            (output.height - 32.0) / maxOf(height, desired.height.toDouble()))
        val scene = panel.gpuFrame()
        return SpoutAdvance(scene, scale, if (compatibility) raster(settings, scale) else null)
    }

    /** Compatibility raster: the 0.12.1 Java2D painter, used only when the native painter is unavailable. */
    private fun raster(settings: IslandSettings, scale: Double): IntArray {
        val output = settings.spout
        if (frame?.width != output.width || frame?.height != output.height) {
            frame?.flush()
            frame = BufferedImage(output.width, output.height, BufferedImage.TYPE_INT_ARGB_PRE)
        }
        val image = frame!!
        val g = image.createGraphics()
        try {
            g.composite = AlphaComposite.Clear; g.fillRect(0, 0, image.width, image.height)
            g.composite = AlphaComposite.SrcOver
            g.translate((image.width - panel.width * scale) / 2, (image.height - panel.height * scale) / 2)
            g.scale(scale, scale); panel.paint(g)
        } finally { g.dispose() }
        return (image.raster.dataBuffer as DataBufferInt).data
    }

    override fun close() { panel.close(); frame?.flush(); frame = null }
}

/**
 * Native painter: draws the Spout presentation straight into the sender's frame buffer, so a frame is
 * rasterized once and uploaded without a second CPU copy. Owned by the sender thread, closed by its caller.
 */
internal class SkiaSpoutPainter : AutoCloseable {
    private val lock = Object()
    private var scene: IslandGpuScene? = null
    private var surface: Surface? = null
    private var address = 0L
    private var width = 0
    private var height = 0
    private var closed = false

    fun paint(target: Pointer, outputWidth: Int, outputHeight: Int, advance: SpoutAdvance) {
        val targetAddress = Pointer.nativeValue(target)
        synchronized(lock) {
            if (closed) return
            // Skiko must be loaded before the first Skia object exists; the desktop renderer may have done it already.
            val painter = scene ?: newScene()
            val destination = surfaceFor(targetAddress, outputWidth, outputHeight)
            val frame = advance.scene
            val canvas = destination.canvas
            canvas.clear(0)
            val hover = IslandHoverGeometry.frame(frame.width, frame.height, frame.settings.notch,
                frame.settings.cornerRoundness, IslandHoverMotion.Pose.SHOWN)
            canvas.scoped {
                canvas.translate(((outputWidth - frame.width * advance.scale) / 2).toFloat(),
                    ((outputHeight - frame.height * advance.scale) / 2).toFloat())
                canvas.scale(advance.scale.toFloat(), advance.scale.toFloat())
                painter.paint(canvas, frame, hover, advance.scale)
            }
            destination.flush()
        }
    }

    private fun newScene(): IslandGpuScene {
        SkikoRuntime.load()
        return IslandGpuScene().also { scene = it }
    }

    private fun surfaceFor(targetAddress: Long, outputWidth: Int, outputHeight: Int): Surface {
        val existing = surface
        if (existing != null && address == targetAddress && width == outputWidth && height == outputHeight) return existing
        existing?.close()
        val created = Surface.makeRasterDirect(ImageInfo(outputWidth, outputHeight, ColorType.BGRA_8888,
            ColorAlphaType.PREMUL), targetAddress, outputWidth * 4)
        surface = created; address = targetAddress; width = outputWidth; height = outputHeight
        return created
    }

    /** Drops Skia state for a reconfiguration; unlike close(), the painter stays usable afterwards. */
    fun reset() = synchronized(lock) { release() }

    private fun release() {
        surface?.close(); surface = null
        scene?.close(); scene = null
        address = 0L; width = 0; height = 0
    }

    override fun close() = synchronized(lock) { closed = true; release() }
}

internal class SpoutOutput(private val report: (Throwable) -> Unit) : AutoCloseable {
    private val sender = SpoutSender(report)
    private val painter = SkiaSpoutPainter()
    private var presentation: SpoutPresentation? = null
    private var configuration: SpoutSettings? = null
    private var compatibility = false
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
            // The native painter is retried for every configuration; a failure falls back to the raster path.
            compatibility = System.getProperty("spw.spout.renderer") == "software"
            painter.reset()
        }
        if (wanted == null || failed || !sender.ready || now < nextFrame) return
        if (!compatibility && sender.nativeFramesFailed) compatibility = true
        val period = if (settings.lowPerformance) 67_000_000L else 1_000_000_000L / wanted.fps
        nextFrame = if (nextFrame == 0L || now - nextFrame >= period) now + period else nextFrame + period
        val dt = if (lastFrame == 0L) period / 1e9 else ((now - lastFrame) / 1e9).coerceIn(0.0, .1)
        lastFrame = now
        try {
            val start = System.nanoTime()
            val advance = (presentation ?: SpoutPresentation(report).also { presentation = it })
                .advance(settings, snapshot, levels, dt, compatibility)
            if (compatibility) sender.offer(advance.pixels!!)
            else sender.offerNative { target, width, height -> painter.paint(target, width, height, advance) }
            val renderNs = System.nanoTime() - start
            maxRenderNs = maxOf(maxRenderNs, renderNs)
            renderSamples[(sampleFrames % renderSamples.size).toInt()] = renderNs
            if (sampleStart == 0L) sampleStart = now
            sampleFrames++
            if (now - sampleStart >= 10_000_000_000L) {
                val samples = renderSamples.copyOf(minOf(sampleFrames.toInt(), renderSamples.size)).sorted()
                println("[SPW Spout] fps=%.1f frames=%d dropped=%d mode=%s paintMaxMs=%.2f uploadMaxMs=%.2f renderP95Ms=%.2f".format(
                    sampleFrames * 1e9 / (now - sampleStart), sender.sentFrames, sender.droppedFrames,
                    if (compatibility) "java2d" else "skia", sender.maxPaintNs / 1e6, sender.maxUploadNs / 1e6,
                    samples[(samples.size * .95).toInt().coerceAtMost(samples.lastIndex)] / 1e6))
                sampleFrames = 0; sampleStart = now; maxRenderNs = 0
            }
        } catch (error: Throwable) {
            failed = true; sender.configure(null); painter.reset(); report(error)
        }
    }

    fun nextDelayMs(): Int? = if (configuration == null || failed || !sender.ready) null else
        ((nextFrame - System.nanoTime()) / 1_000_000).toInt().coerceAtLeast(1)

    override fun close() {
        sender.close(); painter.close(); presentation?.close(); presentation = null
    }
}
