package util

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * Intentionally not using VIPS, so I can validate the image using a different library.
 */
fun byteArrayToImage(byteArray: ByteArray): BufferedImage {
    ByteArrayInputStream(byteArray).use { inputStream ->
        // May return null if format is unsupported
        return ImageIO.read(inputStream) ?: throw IllegalArgumentException("Unsupported image format")
    }
}
