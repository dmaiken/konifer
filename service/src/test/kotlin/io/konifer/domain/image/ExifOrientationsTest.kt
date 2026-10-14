package io.konifer.domain.image

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.common.image.Flip
import io.konifer.common.image.ImageFormat
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.toDimension
import io.konifer.infrastructure.vips.transformer.RotateFlip
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.lang.foreign.ValueLayout

class ExifOrientationsTest {
    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4, 5, 6, 7, 8])
    fun `public rotation and horizontal flip reproduce libvips auto orientation`(orientation: Int) {
        val bytes = javaClass.getResourceAsStream("/images/apollo-11.jpeg")!!.readAllBytes()
        val (rotate, horizontalFlip) = ExifOrientations.fromExifOrientation(orientation)
        Vips.run { arena ->
            val source = VImage.newFromBytes(arena, bytes).set("orientation", orientation)
            val expected = source.autorot()
            val actual =
                RotateFlip
                    .transform(
                        arena,
                        source,
                        Transformation(
                            width = source.width.toDimension(),
                            height = source.height.toDimension(),
                            format = ImageFormat.PNG,
                            colorSpace = ColorSpace.SRGB,
                            rotate = rotate,
                            horizontalFlip = horizontalFlip,
                        ),
                    ).processed

            actual.width shouldBe expected.width
            actual.height shouldBe expected.height
            actual.writeToMemory().toArray(ValueLayout.JAVA_BYTE) shouldBe expected.writeToMemory().toArray(ValueLayout.JAVA_BYTE)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4, 5, 6, 7, 8])
    fun `normalizing explicit rotation and flip preserves the public pair`(orientation: Int) {
        val pair = ExifOrientations.fromExifOrientation(orientation)
        ExifOrientations.normalizeOrientation(pair.first, if (pair.second) Flip.H else Flip.NONE) shouldBe pair
    }
}
