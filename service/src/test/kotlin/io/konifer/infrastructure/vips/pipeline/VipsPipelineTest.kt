package io.konifer.infrastructure.vips.pipeline

import app.photofox.vipsffm.VImage
import io.konifer.domain.image.ImageFormat
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.transformation.AlphaState
import io.konifer.infrastructure.vips.transformation.VipsTransformer
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.lang.foreign.Arena

class VipsPipelineTest {
    val arena = mockk<Arena>()
    val source = mockk<VImage>()
    val transformation =
        Transformation(
            width = 100,
            height = 200,
            format = ImageFormat.PNG,
        )

    @Test
    fun `runs transformers in specified order`() {
        val transformedSource1 = mockk<VImage>()
        val transformedSource2 = mockk<VImage>()
        val transformer1 = mockk<VipsTransformer>()
        val transformer2 = mockk<VipsTransformer>()
        mockRequiredTransformation(
            mock = transformer1,
            transformation = transformation,
            source = source,
            output = transformedSource1,
            name = "transformation 1",
        )
        mockRequiredTransformation(
            mock = transformer2,
            transformation = transformation,
            source = transformedSource1,
            output = transformedSource2,
            name = "transformation 2",
        )

        val result =
            vipsPipeline {
                add(transformer1)
                add(transformer2)
            }.build().run(
                arena = arena,
                source = source,
                transformation = transformation,
            )
        result.processed shouldBe transformedSource2
        result.requiresLqipRegeneration shouldBe true
        result.appliedTransformations shouldHaveSize 2
        result.appliedTransformations.first().apply {
            name shouldBe "transformation 1"
            exceptionMessage shouldBe null
        }
        result.appliedTransformations[1].apply {
            name shouldBe "transformation 2"
            exceptionMessage shouldBe null
        }
    }

    @Test
    fun `premultiplies alpha if necessary`() {
        every { source.hasAlpha() } returns true
        every { source.premultiply() } answers { invocation.self as VImage }
        val transformedSource1 = mockk<VImage>()
        every { transformedSource1.hasAlpha() } returns true
        every { transformedSource1.premultiply() } answers { invocation.self as VImage }
        val transformedSource2 = mockk<VImage>()
        every { transformedSource2.hasAlpha() } returns false
        every { transformedSource2.unpremultiply() } answers { invocation.self as VImage }
        val transformer1 = mockk<VipsTransformer>()
        val transformer2 = mockk<VipsTransformer>()
        mockRequiredTransformation(
            mock = transformer1,
            transformation = transformation,
            source = source,
            output = transformedSource1,
            name = "transformation 1",
            alphaStateRequired = AlphaState.PREMULTIPLIED,
        )
        mockRequiredTransformation(
            mock = transformer2,
            transformation = transformation,
            source = transformedSource1,
            output = transformedSource2,
            name = "transformation 2",
            alphaStateRequired = AlphaState.PREMULTIPLIED,
        )

        val result =
            vipsPipeline {
                add(transformer1)
                add(transformer2)
            }.build().run(
                arena = arena,
                source = source,
                transformation = transformation,
            )
        result.processed shouldBe transformedSource2
        result.requiresLqipRegeneration shouldBe true
        result.appliedTransformations shouldHaveSize 2
        verify {
            source.premultiply()
        }
        // Image will have already been premultiplied
        verify(exactly = 0) {
            transformedSource1.premultiply()
        }
        verify {
            transformedSource2.unpremultiply()
        }
    }

    @Test
    fun `unpremultiplies alpha if necessary`() {
        every { source.hasAlpha() } returns true
        every { source.premultiply() } answers { invocation.self as VImage }
        val transformedSource1 = mockk<VImage>()
        every { transformedSource1.hasAlpha() } returns true
        every { transformedSource1.unpremultiply() } answers { invocation.self as VImage }
        val transformedSource2 = mockk<VImage>()
        val transformer1 = mockk<VipsTransformer>()
        val transformer2 = mockk<VipsTransformer>()
        mockRequiredTransformation(
            mock = transformer1,
            transformation = transformation,
            source = source,
            output = transformedSource1,
            name = "transformation 1",
            alphaStateRequired = AlphaState.PREMULTIPLIED,
        )
        mockRequiredTransformation(
            mock = transformer2,
            transformation = transformation,
            source = transformedSource1,
            output = transformedSource2,
            name = "transformation 2",
            alphaStateRequired = AlphaState.UN_PREMULTIPLIED,
        )

        val result =
            vipsPipeline {
                add(transformer1)
                add(transformer2)
            }.build().run(
                arena = arena,
                source = source,
                transformation = transformation,
            )
        result.processed shouldBe transformedSource2
        result.requiresLqipRegeneration shouldBe true
        result.appliedTransformations shouldHaveSize 2
        verify {
            source.premultiply()
        }
        verify {
            transformedSource1.unpremultiply()
        }
        // Image will have already been unpremultiplied
        verify(exactly = 0) {
            transformedSource2.unpremultiply()
        }
    }

    @Test
    fun `skips alpha premultiplies and unpremultiplies if image has no alpha channel`() {
        every { source.hasAlpha() } returns false
        val transformedSource1 = mockk<VImage>()
        every { transformedSource1.hasAlpha() } returns false
        val transformedSource2 = mockk<VImage>()
        every { transformedSource2.hasAlpha() } returns false
        val transformer1 = mockk<VipsTransformer>()
        val transformer2 = mockk<VipsTransformer>()
        mockRequiredTransformation(
            mock = transformer1,
            transformation = transformation,
            source = source,
            output = transformedSource1,
            name = "transformation 1",
            alphaStateRequired = AlphaState.PREMULTIPLIED,
        )
        mockRequiredTransformation(
            mock = transformer2,
            transformation = transformation,
            source = transformedSource1,
            output = transformedSource2,
            name = "transformation 2",
            alphaStateRequired = AlphaState.PREMULTIPLIED,
        )

        val result =
            vipsPipeline {
                add(transformer1)
                add(transformer2)
            }.build().run(
                arena = arena,
                source = source,
                transformation = transformation,
            )
        result.processed shouldBe transformedSource2
        result.requiresLqipRegeneration shouldBe true
        result.appliedTransformations shouldHaveSize 2
        verify(exactly = 0) {
            source.premultiply()
        }
        verify(exactly = 0) {
            transformedSource1.premultiply()
        }
        verify(exactly = 0) {
            transformedSource2.unpremultiply()
        }
    }

    @Test
    fun `skips transformer if not needed`() {
        val transformedSource1 = mockk<VImage>()
        val transformer1 = mockk<VipsTransformer>()
        val transformer2 = mockk<VipsTransformer>()
        mockUnRequiredTransformation(
            mock = transformer1,
            transformation = transformation,
            source = source,
            name = "transformation 1",
        )
        mockRequiredTransformation(
            mock = transformer2,
            transformation = transformation,
            source = source,
            output = transformedSource1,
            name = "transformation 2",
        )

        val result =
            vipsPipeline {
                add(transformer1)
                add(transformer2)
            }.build().run(
                arena = arena,
                source = source,
                transformation = transformation,
            )
        result.processed shouldBe transformedSource1
        result.requiresLqipRegeneration shouldBe true
        result.appliedTransformations shouldHaveSize 1
        result.appliedTransformations.first().apply {
            name shouldBe "transformation 2"
            exceptionMessage shouldBe null
        }
    }

    @Test
    fun `unmultiplies alpha and terminates if transformer throws an exception`() {
        every { source.hasAlpha() } returns true
        every { source.premultiply() } answers { invocation.self as VImage }
        every { source.unpremultiply() } answers { invocation.self as VImage }
        val transformedSource1 = mockk<VImage>()
        val transformedSource2 = mockk<VImage>()
        val transformer1 = mockk<VipsTransformer>()
        val transformer2 = mockk<VipsTransformer>()
        val exceptionToThrow = IllegalStateException("error message")
        mockRequiredTransformation(
            mock = transformer1,
            transformation = transformation,
            source = source,
            output = transformedSource1,
            name = "transformation 1",
            exceptionToThrow = exceptionToThrow,
            alphaStateRequired = AlphaState.PREMULTIPLIED,
        )
        mockRequiredTransformation(
            mock = transformer2,
            transformation = transformation,
            source = transformedSource1,
            output = transformedSource2,
            name = "transformation 2",
        )

        val result =
            vipsPipeline {
                add(transformer1)
                add(transformer2)
            }.build().run(
                arena = arena,
                source = source,
                transformation = transformation,
            )
        result.processed shouldBe source
        result.requiresLqipRegeneration shouldBe false
        result.appliedTransformations shouldHaveSize 1
        result.appliedTransformations.first().apply {
            name shouldBe "transformation 1"
            exceptionMessage shouldBe exceptionToThrow.message
        }
        verify {
            source.premultiply()
            source.unpremultiply()
        }
    }

    @Test
    fun `if one transformation requires lqip regeneration then the result will require it`() {
        val transformedSource1 = mockk<VImage>()
        val transformedSource2 = mockk<VImage>()
        val transformer1 = mockk<VipsTransformer>()
        val transformer2 = mockk<VipsTransformer>()
        mockRequiredTransformation(
            mock = transformer1,
            transformation = transformation,
            source = source,
            output = transformedSource1,
            name = "transformation 1",
            requireLqipRegeneration = true,
        )
        mockRequiredTransformation(
            mock = transformer2,
            transformation = transformation,
            source = transformedSource1,
            output = transformedSource2,
            name = "transformation 2",
            requireLqipRegeneration = false,
        )

        val result =
            vipsPipeline {
                add(transformer1)
                add(transformer2)
            }.build().run(
                arena = arena,
                source = source,
                transformation = transformation,
            )
        result.processed shouldBe transformedSource2
        result.requiresLqipRegeneration shouldBe true
        result.appliedTransformations shouldHaveSize 2
        result.appliedTransformations.first().apply {
            name shouldBe "transformation 1"
            exceptionMessage shouldBe null
        }
        result.appliedTransformations[1].apply {
            name shouldBe "transformation 2"
            exceptionMessage shouldBe null
        }
    }

    private fun mockRequiredTransformation(
        mock: VipsTransformer,
        transformation: Transformation,
        source: VImage,
        output: VImage,
        name: String,
        alphaStateRequired: AlphaState = AlphaState.UN_PREMULTIPLIED,
        exceptionToThrow: Throwable? = null,
        requireLqipRegeneration: Boolean = true,
    ) {
        exceptionToThrow?.let {
            every {
                mock.transform(
                    arena = arena,
                    source = source,
                    transformation = transformation,
                )
            } throws it
        } ?: run {
            every {
                mock.transform(
                    arena = arena,
                    source = source,
                    transformation = transformation,
                )
            } returns
                VipsTransformationResult(
                    processed = output,
                    requiresLqipRegeneration = requireLqipRegeneration,
                )
        }

        every {
            mock.requiresTransformation(
                arena = arena,
                source = source,
                transformation = transformation,
            )
        } returns true
        every { mock.name } returns name
        every { mock.requiresAlphaState } returns alphaStateRequired
    }

    private fun mockUnRequiredTransformation(
        mock: VipsTransformer,
        transformation: Transformation,
        source: VImage,
        name: String,
    ) {
        every {
            mock.requiresTransformation(
                arena = arena,
                source = source,
                transformation = transformation,
            )
        } returns false
        every {
            mock.name
        } returns name
    }
}
