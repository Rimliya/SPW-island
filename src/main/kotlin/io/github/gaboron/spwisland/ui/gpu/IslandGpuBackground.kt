// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui.gpu

import io.github.gaboron.spwisland.core.*
import io.github.gaboron.spwisland.ui.*
import org.jetbrains.skia.*
import java.awt.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.roundToInt
import io.github.gaboron.spwisland.platform.WindowsDesktopBackdrop

internal class IslandGpuBackground(private val resources: IslandGpuResources) : AutoCloseable {
    private val glass = IslandLiquidGlass()
    val glassFailure get() = glass.failure
    val materialShader get() = glass.materialShader
    var waitingForCrop = false
        private set

    fun draw(canvas: Canvas, frame: IslandGpuFrame, shape: IslandHoverGeometry.Frame, palette: IslandPalette,
             backdrop: WindowsDesktopBackdrop.Sample?): Boolean {
        val path = resources.path(shape.shape)
        val duration = frame.snapshot.metadata.durationMs
        val progress = if (duration > 0) (frame.snapshot.positionMs.toDouble() / duration).coerceIn(0.0, 1.0) else 0.0
        val attempted = frame.settings.backgroundStyle == IslandBackgroundStyle.LIQUID_GLASS &&
            !frame.settings.lowPerformance && backdrop != null
        val fill = frame.progressMode == BackgroundProgressMode.FILL && frame.snapshot.track != null &&
            frame.snapshot.status != PlaybackStatus.IDLE && frame.settings.performance.renderBackgroundProgress
        val liquid = attempted && glass.draw(canvas, path, shape.shape, shape.maskKey,
            (minOf(frame.width, frame.height) - 1f).coerceAtLeast(1f), checkNotNull(backdrop), frame.settings.opacity,
            progress.toFloat(), if (fill) frame.progressOpacity.toFloat() else 0f, frame.width)
        waitingForCrop = attempted && !liquid && glass.failure == null
        drawMaterial(canvas, frame, shape, palette, path, progress, liquid, replace = attempted && !liquid)
        if (!liquid) canvas.drawPath(path, resources.stroke(0x13ffffff, 1f))
        return liquid
    }

    fun drawHighlight(canvas: Canvas, shape: IslandHoverGeometry.Frame) =
        glass.drawHighlight(canvas, resources.path(shape.shape))

    private fun drawMaterial(canvas: Canvas, frame: IslandGpuFrame, shape: IslandHoverGeometry.Frame,
                             palette: IslandPalette, path: Path, progress: Double, liquid: Boolean,
                             replace: Boolean = false) {
        val alpha = frame.settings.opacity * 255 / 100
        val background = palette.background
        if (!liquid) classic(canvas, frame, palette, path, alpha, replace)
        if (progress > 0 && frame.snapshot.track != null && frame.snapshot.status != PlaybackStatus.IDLE &&
            frame.settings.performance.renderBackgroundProgress && frame.progressOpacity > 0) {
            when (frame.progressMode) {
                BackgroundProgressMode.FILL -> canvas.scoped {
                    if (liquid) return@scoped // Optical shader already applies the existing fill progress.
                    canvas.clipRect(Rect.makeXYWH(0f, 0f, (frame.width * progress).toFloat(), frame.height.toFloat()), ClipMode.INTERSECT, true)
                    val bg = Color.RGBtoHSB(background.red, background.green, background.blue, null)
                    val fg = Color.RGBtoHSB(palette.lyric.red, palette.lyric.green, palette.lyric.blue, null)
                    val filled = Color(Color.HSBtoRGB(bg[0], bg[1] * .82f, (bg[2] + .10f).coerceAtMost(fg[2] * .35f)))
                    fun mix(a: Int, b: Int) = (a + (b - a) * frame.progressOpacity).roundToInt().coerceIn(0, 255)
                    val color = (alpha shl 24) or (mix(background.red, filled.red) shl 16) or
                        (mix(background.green, filled.green) shl 8) or mix(background.blue, filled.blue)
                    canvas.drawPath(path, resources.fill(color, blend = BlendMode.SRC))
                }
                BackgroundProgressMode.TOP_LINE -> {
                    val inset = IslandGeometry.progressEdgeInset(frame.width, frame.height, frame.settings.shape, frame.settings.cornerRoundness)
                    val start = inset.coerceAtLeast(2.0).toFloat()
                    val end = (frame.width - inset).coerceAtLeast(start.toDouble()).toFloat()
                    val y = if (frame.settings.notch) shape.shape.bounds2D.maxY - .75 else shape.shape.bounds2D.minY + .75
                    edge(canvas, start, end, end, y.toFloat(), palette.lyric.rgb,
                        (42 * frame.progressOpacity).roundToInt())
                    edge(canvas, start, end, start + (end - start) * progress.toFloat(), y.toFloat(), palette.lyric.rgb,
                        (palette.lyric.alpha * frame.progressOpacity).roundToInt())
                }
                BackgroundProgressMode.OFF -> Unit
            }
        }
    }

    override fun close() = glass.close()

    private fun classic(canvas: Canvas, frame: IslandGpuFrame, palette: IslandPalette, path: Path,
                        alpha: Int, replace: Boolean) {
        val blend = if (replace) BlendMode.SRC else BlendMode.SRC_OVER
        when (frame.settings.backgroundMaterial) {
            BackgroundMaterial.SOLID ->
                canvas.drawPath(path, resources.fill((palette.background.rgb and 0xffffff) or (alpha shl 24), blend = blend))
            BackgroundMaterial.LINEAR_GRADIENT -> {
                val start = IslandVisualColors.parseHex(frame.settings.gradientStartColor)
                    ?: IslandVisualColors.shade(palette.background, 1.45)
                val end = IslandVisualColors.parseHex(frame.settings.gradientEndColor) ?: palette.spectrum
                val angle = Math.toRadians(frame.settings.gradientAngle.toDouble())
                val dx = cos(angle).toFloat()
                val dy = sin(angle).toFloat()
                val half = (abs(dx) * frame.width + abs(dy) * frame.height) / 2f
                val cx = frame.width / 2f
                val cy = frame.height / 2f
                val paint = resources.linearGradient(cx - dx * half, cy - dy * half,
                    cx + dx * half, cy + dy * half,
                    intArrayOf(IslandVisualColors.argb(IslandVisualColors.withAlpha(start, alpha)),
                        IslandVisualColors.argb(IslandVisualColors.withAlpha(end, alpha))),
                    floatArrayOf(0f, 1f), blend = blend)
                canvas.drawPath(path, paint)
            }
            BackgroundMaterial.FLOWING_LIGHT -> {
                canvas.drawPath(path, resources.fill((palette.background.rgb and 0xffffff) or (alpha shl 24), blend = blend))
                val accents = IslandVisualColors.accents(palette)
                val seconds = System.currentTimeMillis() / 1000.0 * frame.settings.flowSpeed / 100.0
                val radius = max(frame.width, frame.height) * .62f
                for (index in accents.indices) {
                    val phase = index * 1.37
                    val cx = frame.width * (.15f + .18f * index) + sin(seconds * .32 + phase).toFloat() * frame.width * .16f
                    val cy = frame.height * .5f + cos(seconds * .27 + phase * 1.7).toFloat() * frame.height * .42f
                    val color = accents[index % accents.size]
                    for (layer in 0 until 7) {
                        val scale = 1.0 - layer * .12
                        val fade = ((layer + 1) * 4).coerceAtMost(30) * frame.settings.opacity / 100
                        if (fade > 0) canvas.drawCircle(cx, cy, radius * scale.toFloat(),
                            resources.fill(IslandVisualColors.argb(IslandVisualColors.withAlpha(color, fade))))
                    }
                }
            }
            BackgroundMaterial.COVER_MOSAIC -> {
                canvas.drawPath(path, resources.fill((palette.background.rgb and 0xffffff) or (alpha shl 24), blend = blend))
                val cover = frame.snapshot.metadata.cover
                if (cover != null) {
                    val source = minOf(cover.width, cover.height).toFloat()
                    canvas.drawImageRect(resources.cover(cover),
                        Rect.makeXYWH((cover.width - source) / 2, (cover.height - source) / 2, source, source),
                        Rect.makeWH(frame.width.toFloat(), frame.height.toFloat()), SamplingMode.LINEAR,
                        resources.fill(0xffffffff.toInt(), frame.settings.opacity / 180f), true)
                    canvas.drawPath(path, resources.fill(
                        ((frame.settings.opacity * 110 / 100) shl 24) or 0x040509))
                }
                val accents = IslandVisualColors.accents(palette)
                val block = frame.settings.mosaicBlock.coerceIn(4, 64)
                val blockAlpha = 34 * frame.settings.opacity / 100
                var y = 0
                var row = 0
                while (y < frame.height) {
                    var x = 0
                    var column = 0
                    while (x < frame.width) {
                        val color = accents[(row * 7 + column * 3) % accents.size]
                        canvas.drawRect(Rect.makeXYWH(x.toFloat(), y.toFloat(), block.toFloat(), block.toFloat()),
                            resources.fill(IslandVisualColors.argb(IslandVisualColors.withAlpha(color, blockAlpha))))
                        x += block
                        column++
                    }
                    y += block
                    row++
                }
            }
        }
    }

    private fun edge(canvas: Canvas, start: Float, end: Float, filled: Float, y: Float, color: Int, alpha: Int) {
        val rgb = color and 0xffffff
        val paint = resources.gradient(start, end, intArrayOf(rgb, rgb or (alpha shl 24), rgb or (alpha shl 24), rgb),
            floatArrayOf(0f, .06f, .94f, 1f))
        // A native rounded rectangle is the filled form of this 1.5 px round-capped line.
        canvas.drawRRect(RRect.makeXYWH(start - .75f, y - .75f, filled - start + 1.5f, 1.5f, .75f), paint)
    }
}
