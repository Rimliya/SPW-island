// SPDX-License-Identifier: GPL-3.0-only
package io.github.solitaire.spwisland.ui.gpu

import io.github.solitaire.spwisland.core.*
import io.github.solitaire.spwisland.longtone.LongToneAnalysis
import io.github.solitaire.spwisland.ui.*
import org.jetbrains.skia.*
import java.awt.Color
import java.awt.geom.Rectangle2D

/** Draws cached native glyph paths directly, sharing the existing timing and font shaping. */
internal class IslandGpuLyrics(private val resources: IslandGpuResources) {
    val adaptiveContrast = IslandLyricContrast()

    fun rows(canvas: Canvas, frame: IslandGpuFrame, width: Float, height: Float, inset: Float) {
        IslandLyricPresentation.rows(frame.lyrics, frame.previousLyrics, height, frame.transition, frame.settings.fontSize).forEach { pose ->
            val snapshot = if (pose.previous) frame.outgoing ?: return@forEach else frame.snapshot
            val block = pose.layout.block
            val palette = IslandPalette.from(frame.settings, snapshot.metadata.coverRgb, snapshot.metadata.cover)
            val time = snapshot.positionMs + frame.settings.offsetMs
            canvas.scoped {
                canvas.clipRect(Rect.makeXYWH(inset - 8, 2f, width - inset * 2 + 16, height - 4f), ClipMode.INTERSECT, true)
                canvas.translate(width / 2, height / 2 + pose.offset.toFloat())
                canvas.scale(pose.scale.toFloat(), pose.scale.toFloat()); canvas.translate(-width / 2, -height / 2)
                line(canvas, block.shapedMain, block.main, block.timedWords,
                    if (block.timedWords.isNotEmpty()) time else (time - (block.line?.startMs ?: 0)).coerceAtLeast(0),
                    inset, pose.layout.mainBaseline, width - inset * 2, block.mainFont.size2D,
                    frame.settings.karaoke, frame.settings.performance.detailedKaraoke, palette.lyric.rgb,
                    palette.lyricOutline, pose.opacity, frame.longTones, adaptive = true,
                    allowLongTone = block.allowLongTone, alignment = frame.settings.textAlignment,
                    outlineStyle = frame.settings.outlineStyle)
                block.shapedSub?.let { shaped -> line(canvas, shaped, block.sub.orEmpty(), emptyList(),
                    (time - (block.line?.startMs ?: 0)).coerceAtLeast(0), inset, pose.layout.subBaseline,
                    width - inset * 2, block.subFont.size2D, false, false, 0xffb1b6c3.toInt(),
                    palette.lyricOutline, pose.opacity, frame.longTones, adaptive = true,
                    alignment = frame.settings.textAlignment, outlineStyle = frame.settings.outlineStyle) }
            }
        }
    }

    fun line(canvas: Canvas, shaped: ShapedText, text: String, words: List<Word>, time: Long,
             x: Float, baseline: Float, available: Float, size: Float, karaoke: Boolean, detailed: Boolean,
             color: Int, contrast: Boolean, alpha: Float, longTones: LongToneAnalysis? = null,
             adaptive: Boolean = false, allowLongTone: Boolean = true,
             alignment: IslandTextAlignment = IslandTextAlignment.CENTER,
             outlineStyle: LyricOutlineStyle = LyricOutlineStyle.HARD) {
        if (text.isEmpty() || available <= 0) return
        val adaptiveLine = adaptive && adaptiveContrast.active
        val placement = LyricTextPlacement.calculate(shaped, text, words, time, x, available, karaoke,
            detailed, alignment)
        canvas.scoped {
            canvas.clipRect(Rect.makeXYWH(x - size * .16f, baseline - shaped.layout.ascent - size * .3f,
                available + size * .32f, shaped.layout.ascent + shaped.layout.descent + size * .6f), ClipMode.INTERSECT, true)
            canvas.translate(placement.origin, baseline)
            if (adaptiveLine) adaptiveContrast.prepareLine(canvas,
                maxOf(shaped.left, x - placement.origin), minOf(shaped.right, x + available - placement.origin),
                shaped.layout.ascent, shaped.layout.descent, color)
            when {
                !karaoke || words.isEmpty() -> {
                    val path = resources.path(shaped.outline)
                    val paint = resources.fill(color, alpha)
                    outline(canvas, path, size, contrast, alpha, adaptiveLine, outlineStyle)
                    canvas.drawPath(path, paint)
                }
                placement.lightweight -> {
                    val path = resources.path(shaped.outline)
                    outline(canvas, path, size, contrast, alpha, adaptiveLine, outlineStyle)
                    canvas.drawPath(path, resources.fill(DIM, alpha))
                    val width = if (shaped.layout.isLeftToRight) placement.boundary - shaped.left else shaped.right - placement.boundary
                    if (width > 0) {
                        val start = if (shaped.layout.isLeftToRight) shaped.left else placement.boundary
                        canvas.clipRect(Rect.makeXYWH(start, -shaped.layout.ascent, width, shaped.layout.ascent + shaped.layout.descent), ClipMode.INTERSECT, true)
                        canvas.drawPath(path, resources.fill(color, alpha))
                    }
                }
                placement.geometry == null -> timed(canvas, shaped, words, time, size, color, contrast, alpha,
                    adaptiveLine, outlineStyle)
                else -> words(canvas, shaped, words, placement.geometry, time, size, color, contrast, alpha,
                    longTones.takeIf { allowLongTone }, adaptiveLine, outlineStyle)
            }
        }
    }

    private fun words(canvas: Canvas, shaped: ShapedText, words: List<Word>, geometry: List<WordGeometry.Cell>,
                      time: Long, size: Float, color: Int, contrast: Boolean, alpha: Float,
                      longTones: LongToneAnalysis?, adaptive: Boolean,
                      outlineStyle: LyricOutlineStyle = LyricOutlineStyle.HARD) {
        val flags = longTones?.forWords(words)
        for ((wordIndex, word) in words.withIndex()) {
            val cell = geometry[wordIndex]
            val paint = highlight(shaped.layout.isLeftToRight, cell.bounds, word.progress(time), size, color, alpha)
            for ((index, cluster) in cell.clusters.withIndex()) {
                val pose = AmllMotion.word(word, time, index, cell.clusters.size, wordIndex == words.lastIndex, flags?.get(wordIndex) ?: false)
                val path = resources.path(cluster.shape)
                val bounds = cluster.bounds
                canvas.scoped {
                    canvas.translate((pose.xEm * size + bounds.centerX).toFloat(), (pose.yEm * size + bounds.centerY).toFloat())
                    canvas.scale(pose.scale.toFloat(), pose.scale.toFloat())
                    canvas.translate(-bounds.centerX.toFloat(), -bounds.centerY.toFloat())
                    glow(canvas, path, size, color, pose.glow, alpha)
                    outline(canvas, path, size, contrast, alpha, adaptive, outlineStyle)
                    canvas.drawPath(path, paint)
                }
            }
        }
    }

    private fun timed(canvas: Canvas, shaped: ShapedText, words: List<Word>, time: Long, size: Float,
                      color: Int, contrast: Boolean, alpha: Float, adaptive: Boolean,
                      outlineStyle: LyricOutlineStyle = LyricOutlineStyle.HARD) {
        outline(canvas, resources.path(shaped.outline), size, contrast, alpha, adaptive, outlineStyle)
        var start = 0
        for (word in words) {
            val end = start + word.text.length
            canvas.scoped {
                val region = resources.region(shaped, start, end)
                canvas.clipPath(region, ClipMode.INTERSECT, true)
                val paint = highlight(shaped.layout.isLeftToRight,
                    shaped.layout.getLogicalHighlightShape(start, end).bounds2D, word.progress(time), size, color, alpha)
                // Region lookups can evict the outline from the bounded native path cache.
                canvas.drawPath(resources.path(shaped.outline), paint)
            }
            start = end
        }
    }

    private fun outline(canvas: Canvas, path: Path, size: Float, contrast: Boolean, alpha: Float,
                        adaptive: Boolean, style: LyricOutlineStyle) {
        if (adaptive && adaptiveContrast.draw(canvas, path,
                resources.stroke(0xc8000000.toInt(), (size * .09f).coerceAtLeast(1.5f), alpha))) return
        if (contrast) layers(canvas, path, size, alpha, style)
    }

    private fun highlight(ltr: Boolean, bounds: Rectangle2D, progress: Double, size: Float, color: Int, alpha: Float): Paint {
        if (progress <= 0) return resources.fill(DIM, alpha)
        if (progress >= 1) return resources.fill(color, alpha)
        val boundary = (if (ltr) bounds.x + bounds.width * progress else bounds.maxX - bounds.width * progress).toFloat()
        val feather = minOf(size * .45, bounds.width * .6).toFloat().coerceAtLeast(.01f)
        return resources.gradient(boundary - feather / 2, boundary + feather / 2,
            if (ltr) intArrayOf(color, DIM) else intArrayOf(DIM, color), floatArrayOf(0f, 1f), alpha)
    }

    private fun layers(canvas: Canvas, path: Path, size: Float, alpha: Float, style: LyricOutlineStyle) {
        val edge = (size * .09f).coerceAtLeast(1.5f)
        when (style) {
            LyricOutlineStyle.HARD -> canvas.drawPath(path, resources.stroke(0xc8000000.toInt(), edge, alpha))
            LyricOutlineStyle.GRADIENT -> {
                canvas.drawPath(path, resources.stroke(0x26000000, edge * 3.2f, alpha))
                canvas.drawPath(path, resources.stroke(0x56000000, edge * 2.1f, alpha))
                canvas.drawPath(path, resources.stroke(0xba000000.toInt(), edge, alpha))
            }
            LyricOutlineStyle.DISSOLVE -> dissolve(canvas, path, size, edge, alpha)
        }
    }

    private fun dissolve(canvas: Canvas, path: Path, size: Float, edge: Float, alpha: Float) {
        val paint = resources.outlineStroke()
        try {
            for (layer in DISSOLVE_LAYERS.indices.reversed()) {
                paint.pathEffect = resources.dissolveDash(size, layer)
                paint.strokeWidth = edge * DISSOLVE_LAYERS[layer]
                paint.color = 0xff000000.toInt()
                paint.setAlphaf(DISSOLVE_ALPHAS[layer] / 255f * alpha)
                canvas.drawPath(path, paint)
            }
        } finally {
            paint.pathEffect = null
        }
    }

    private fun glow(canvas: Canvas, path: Path, size: Float, color: Int, emphasis: Double, alpha: Float) {
        val strength = emphasis.coerceIn(0.0, 1.0)
        if (strength <= .001) return
        val hsb = Color.RGBtoHSB(color ushr 16 and 255, color ushr 8 and 255, color and 255, null)
        val tint = Color.HSBtoRGB(hsb[0], minOf(hsb[1], .18f), 1f) and 0xffffff
        val wide = (strength * 58).toInt(); val narrow = (strength * 124).toInt()
        if (wide > 0) canvas.drawPath(path, resources.stroke(tint or (wide shl 24), (size * .16f).coerceAtLeast(1f), alpha))
        if (narrow > 0) canvas.drawPath(path, resources.stroke(tint or (narrow shl 24), (size * .06f).coerceAtLeast(1f), alpha))
    }

    companion object {
        private const val DIM = 0xff7e818a.toInt()
        private val DISSOLVE_LAYERS = floatArrayOf(.9f, 1.5f, 2.2f, 3.0f, 3.9f)
        private val DISSOLVE_ALPHAS = intArrayOf(24, 34, 44, 36, 18)
    }
}
