// SPDX-License-Identifier: GPL-3.0-only
package io.github.solitaire.spwisland.ui

import io.github.solitaire.spwisland.core.IslandShape
import java.awt.Shape
import java.awt.Insets
import java.awt.geom.AffineTransform
import kotlin.math.ceil

/** Maps visibility motion to a sticky notch or a centred body, without scaling content. */
internal object IslandHoverGeometry {
    data class Frame(val shape: Shape, val maskKey: Any, val opacity: Double,
                     val contentOpacity: Double, val contentOffsetY: Double)

    fun frame(width: Int, height: Int, shape: IslandShape, roundness: Int, squircle: Boolean,
              pose: IslandHoverMotion.Pose): Frame {
        val reveal = pose.reveal
        val w = (width - 1.0).coerceAtLeast(0.0)
        val h = (height - 1.0).coerceAtLeast(0.0)
        val notch = shape == IslandShape.NOTCH
        val silhouette = if (reveal == 1.0) IslandGeometry.silhouette(width, height, shape, roundness, squircle)
            else if (notch) IslandNotchGeometry.silhouette(w, h, roundness, reveal)
            else revealShape(w, h, shape, roundness, squircle, reveal)
        return Frame(silhouette, listOf(width, height, shape, roundness, squircle, reveal),
            smooth(reveal / .08), smooth((reveal - .62) / .38),
            if (notch) (reveal - 1.0) * h * .35 else 0.0)
    }

    /** Reserve both the spreading attachment and the reveal crest before the animation starts. */
    fun padding(width: Int, height: Int, notch: Boolean): Insets {
        val spread = if (notch) IslandNotchGeometry.attachmentSpread(width.toDouble(), height.toDouble()) else 0.0
        val horizontal = ceil(maxOf(spread, width * .04) + 2.0).toInt()
        val bottom = if (notch) ceil(height * .06 + 2.0).toInt() else 2
        return Insets(if (notch) 0 else 2, horizontal, bottom, horizontal)
    }

    private fun revealShape(width: Double, height: Double, shape: IslandShape, roundness: Int,
                            squircle: Boolean, reveal: Double): Shape {
        val diameter = minOf(width, height)
        val circle = smooth(reveal / CIRCLE_PHASE)
        val elongation = ((reveal - CIRCLE_PHASE) / (1.0 - CIRCLE_PHASE)).coerceAtLeast(0.0)
        // Zero slope at the circle joins the two phases; the unit slope at full width carries the crest.
        val stretch = if (elongation <= 1.0) elongation * elongation * (2.0 - elongation) else elongation
        val actualHeight = diameter * circle + (height - diameter) * stretch.coerceAtMost(1.0)
        val actualWidth = diameter * circle + (width - diameter) * stretch
        val target = shape.roundness(roundness).toDouble()
        val corner = 100.0 + (target - 100.0) * smooth(elongation)
        val body = ContinuousCornerPath.roundedRectangle(actualWidth, actualHeight, corner, squircle)
        return AffineTransform.getTranslateInstance(
            .5 + (width - actualWidth) / 2.0, .5 + (height - actualHeight) / 2.0
        ).createTransformedShape(body)
    }

    private fun smooth(value: Double): Double = value.coerceIn(0.0, 1.0).let { it * it * (3.0 - 2.0 * it) }
    private const val CIRCLE_PHASE = .38
}
