// SPDX-License-Identifier: GPL-3.0-only
package io.github.solitaire.spwisland.ui

import io.github.solitaire.spwisland.core.CoverArtwork
import java.awt.image.BufferedImage

/** Converts the serialized cover buffer once; callers reuse the immutable raster. */
internal object IslandArtworkImages {
    private var cachedArtwork: CoverArtwork? = null
    private var cachedImage: BufferedImage? = null

    @Synchronized
    fun image(artwork: CoverArtwork): BufferedImage {
        if (cachedArtwork !== artwork || cachedImage == null) {
            cachedArtwork = artwork
            cachedImage = BufferedImage(artwork.width, artwork.height, BufferedImage.TYPE_INT_ARGB).apply {
                setRGB(0, 0, width, height, artwork.argb, 0, width)
            }
        }
        return cachedImage!!
    }
}
