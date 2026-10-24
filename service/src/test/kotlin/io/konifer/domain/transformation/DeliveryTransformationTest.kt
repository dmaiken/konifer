package io.konifer.domain.transformation

import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Flip
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.Rotate
import io.konifer.common.image.TransformableColorSpace
import io.konifer.common.image.pixel.PixelLayout
import io.konifer.createRequestedImageTransformation
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.pixel.toPixelChannels
import io.konifer.domain.variant.attribute.Attributes
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DeliveryTransformationTest {
    private val normalizer = TransformationNormalizer()
    private val original = Attributes(640.toDimension(), 480.toDimension(), ImageFormat.JPEG, orientation = 6, colorSpace = ColorSpace.SRGB)

    @Test
    fun `expanded delivery transformations preserve normalized identity and rendering intent`() {
        val requests =
            listOf(
                RequestedTransformation(),
                createRequestedImageTransformation(width = 101),
                createRequestedImageTransformation(height = 103, rotate = Rotate.AUTO),
                createRequestedImageTransformation(width = 101, rotate = Rotate.NINETY, flip = Flip.H),
                createRequestedImageTransformation(
                    width = 100,
                    height = 50,
                    fit = Fit.FILL,
                    gravity = Gravity.CENTER,
                    pad = 3,
                    padColor = "#12345678",
                    strip = "exif,icc",
                    blur = 2,
                    quality = 75,
                    format = ImageFormat.PNG,
                ),
                createRequestedImageTransformation(width = 100, format = ImageFormat.JPEG, pad = 2),
                createRequestedImageTransformation(width = 100, filter = Filter.SEPIA, colorSpace = TransformableColorSpace.GRAYSCALE),
                RequestedTransformation(
                    width = 100.toDimension(),
                    format = ImageFormat.PIXELS,
                    pixelChannels = "argb".toPixelChannels(),
                    pixelLayout = PixelLayout.PLANAR,
                ),
                RequestedTransformation(width = 100.toDimension(), format = ImageFormat.PIXELS, pixelChannels = "gray".toPixelChannels()),
            )
        requests.forEach { request ->
            val resolved = normalizer.resolve(request, original)
            val expanded = request.forDelivery(resolved)
            val replayed = normalizer.resolve(expanded, original)
            replayed shouldBe resolved
            replayed.isAutoRotate shouldBe resolved.isAutoRotate
            replayed.isColorSpaceLocked shouldBe resolved.isColorSpaceLocked
            replayed.canUpscale shouldBe resolved.canUpscale
            expanded.width shouldBe resolved.width
            expanded.height shouldBe resolved.height
            expanded.format shouldBe resolved.format
        }
    }
}
