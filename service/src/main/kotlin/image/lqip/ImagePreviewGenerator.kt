package io.image.hash

import com.vanniktech.blurhash.BlurHash
import image.lqip.ThumbHash
import image.model.LQIPs
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.path.configuration.PathConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.util.Base64
import javax.imageio.ImageIO

class ImagePreviewGenerator {
    companion object {
        const val MAX_WIDTH = 100
        const val MAX_HEIGHT = 100
    }

    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun generatePreviews(
        imageChannel: ByteChannel?,
        pathConfiguration: PathConfiguration,
    ): LQIPs =
        withContext(Dispatchers.IO) {
            if (imageChannel == null) {
                logger.debug("No image in channel, skipping preview generation.")
                return@withContext LQIPs.NONE
            }
            if (pathConfiguration.imageProperties.previews.isEmpty()) {
                logger.debug("No preview implementations enabled for path, skipping preview generation.")
                return@withContext LQIPs.NONE
            }

            val image = ImageIO.read(imageChannel.toInputStream())

            if (image.width > MAX_WIDTH || image.height > MAX_HEIGHT) {
                throw IllegalArgumentException("Image must be smaller than ${MAX_WIDTH}x$MAX_HEIGHT to generate previews")
            }

            val blurHash =
                if (pathConfiguration.imageProperties.previews.contains(LQIPImplementation.BLURHASH)) {
                    async {
                        BlurHash.encode(
                            bufferedImage = image,
                            componentX = 4,
                            componentY = 4,
                        )
                    }
                } else {
                    null
                }

            val thumbHash =
                if (pathConfiguration.imageProperties.previews.contains(LQIPImplementation.THUMBHASH)) {
                    async {
                        Base64.getEncoder().encodeToString(ThumbHash.rgbaToThumbHash(image.width, image.height, toRgba(image)))
                    }
                } else {
                    null
                }

            LQIPs(
                blurhash = blurHash?.await(),
                thumbhash = thumbHash?.await(),
            )
        }

    private fun toRgba(image: BufferedImage): ByteArray {
        // Force image to TYPE_INT_ARGB to ensure predictable channel layout (ARGB)
        val argbImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        val g = argbImage.createGraphics()
        g.drawImage(image, 0, 0, null)
        g.dispose()

        val rgbaBytes = ByteArray(image.width * image.height * 4)
        val pixels = IntArray(image.width * image.height)
        argbImage.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)

        // Convert ARGB int[] to RGBA byte[]
        for (i in pixels.indices) {
            val argb = pixels[i]
            val r = (argb shr 16) and 0xFF
            val gVal = (argb shr 8) and 0xFF
            val b = (argb) and 0xFF
            val a = (argb shr 24) and 0xFF

            val base = i * 4
            rgbaBytes[base] = r.toByte()
            rgbaBytes[base + 1] = gVal.toByte()
            rgbaBytes[base + 2] = b.toByte()
            rgbaBytes[base + 3] = a.toByte()
        }

        return rgbaBytes
    }
}
