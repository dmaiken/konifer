package io.konifer.domain.transformation

import io.konifer.createImagePreProcessingProperties
import io.konifer.createRequestedImageTransformation
import io.konifer.domain.ports.VariantProfileRepository
import io.konifer.domain.variant.TransformProperties
import io.konifer.domain.variant.TransformationLimitProperties
import io.konifer.domain.variant.preprocessing.PreProcessingProperties
import io.konifer.domain.variant.toPixelCount
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class TransformConfigurationValidatorTest {
    private val variantProfileRepository = mockk<VariantProfileRepository>()
    private val validator = TransformConfigurationValidator(variantProfileRepository)

    @Test
    fun `validates every eager variant against the configured limits`() {
        every { variantProfileRepository.fetch("small") } returns
            createRequestedImageTransformation(width = 100)
        every { variantProfileRepository.fetch("tall") } returns
            createRequestedImageTransformation(height = 200)

        shouldNotThrowAny {
            validator.validate(
                TransformProperties(
                    eagerVariants = listOf("small", "tall"),
                    limits = limits(),
                ),
            )
        }

        verify(exactly = 1) { variantProfileRepository.fetch("small") }
        verify(exactly = 1) { variantProfileRepository.fetch("tall") }
    }

    @Test
    fun `wraps eager variant validation failures with the profile name`() {
        every { variantProfileRepository.fetch("too-wide") } returns
            createRequestedImageTransformation(width = 101)

        val exception =
            shouldThrow<ConfiguredTransformationValidationException> {
                validator.validate(
                    TransformProperties(
                        eagerVariants = listOf("too-wide"),
                        limits = limits(),
                    ),
                )
            }

        exception.message shouldBe "Eager variant 'too-wide' validation failed"
        exception.cause.shouldBeInstanceOf<InvalidTransformationException>()
        exception.cause.message shouldBe "width 101 must not exceed 100 limit"
    }

    @Test
    fun `validates enabled preprocessing against the configured limits`() {
        val exception =
            shouldThrow<ConfiguredTransformationValidationException> {
                validator.validate(
                    TransformProperties(
                        preProcessing =
                            PreProcessingProperties(
                                enabled = true,
                                image = createImagePreProcessingProperties(height = 201),
                            ),
                        limits = limits(),
                    ),
                )
            }

        exception.message shouldBe "Preprocessing validation failed"
        exception.cause.shouldBeInstanceOf<InvalidTransformationException>()
        exception.cause.message shouldBe "height 201 must not exceed 200 limit"
    }

    @Test
    fun `does not validate disabled preprocessing`() {
        shouldNotThrowAny {
            validator.validate(
                TransformProperties(
                    preProcessing =
                        PreProcessingProperties(
                            enabled = false,
                            image = createImagePreProcessingProperties(width = 101, height = 201),
                        ),
                    limits = limits(),
                ),
            )
        }

        verify(exactly = 0) { variantProfileRepository.fetch(any()) }
    }

    private fun limits(): TransformationLimitProperties =
        TransformationLimitProperties(
            maxWidth = 100.toDimension(),
            maxHeight = 200.toDimension(),
            maxPixels = 20_000L.toPixelCount(),
        )
}
