package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.Rotate
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.variant.Transformation
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.lang.foreign.Arena

class AutoRotateTest {
    private val arena = mockk<Arena>()
    private val source = mockk<VImage>()

    @Test
    fun `skips transformations that are not auto-rotation`() {
        AutoRotate.decide(context(rotate = Rotate.NINETY, isAutoRotate = false)) shouldBe TransformationDecision.Skip
    }

    @Test
    fun `auto-rotation without vertical reordering remains sequential`() {
        AutoRotate.decide(context(rotate = Rotate.ZERO, isAutoRotate = true)) shouldBe
            TransformationDecision.Apply(
                requiredAlpha = AlphaRequirement.EITHER,
                requiredPixelAccess = PixelAccess.SEQUENTIAL,
            )
    }

    @ParameterizedTest
    @EnumSource(Rotate::class, mode = EnumSource.Mode.EXCLUDE, names = ["ZERO"])
    fun `auto-rotation with vertical reordering requires random access`(rotate: Rotate) {
        AutoRotate.decide(context(rotate = rotate, isAutoRotate = true)) shouldBe
            TransformationDecision.Apply(
                requiredAlpha = AlphaRequirement.EITHER,
                requiredPixelAccess = PixelAccess.RANDOM,
            )
    }

    private fun context(
        rotate: Rotate,
        isAutoRotate: Boolean,
    ): TransformationContext =
        TransformationContext(
            arena = arena,
            source = source,
            transformation =
                Transformation(
                    width = 10,
                    height = 10,
                    format = ImageFormat.PNG,
                    rotate = rotate,
                    colorSpace = ColorSpace.SRGB,
                    isAutoRotate = isAutoRotate,
                ),
            appliedTransformations = emptyList(),
        )
}
