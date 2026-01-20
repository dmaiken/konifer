package io.konifer.domain.image

import io.konifer.createImagePreProcessingProperties
import io.konifer.domain.variant.preprocessing.ImagePreProcessingProperties
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ImagePreProcessingPropertiesTest {
    @ParameterizedTest
    @ValueSource(ints = [-1, 0])
    fun `ImagePreProcessingProperties maxHeight cannot be less than 0`(maxHeight: Int) {
        val exception =
            shouldThrow<IllegalArgumentException> {
                createImagePreProcessingProperties(
                    maxHeight = maxHeight,
                )
            }

        exception.message shouldBe "'max-height' must be greater than 0"
    }

    @ParameterizedTest
    @ValueSource(ints = [-1, 0])
    fun `ImagePreProcessingProperties maxWidth cannot be less than 0`(maxWidth: Int) {
        val exception =
            shouldThrow<IllegalArgumentException> {
                createImagePreProcessingProperties(
                    maxWidth = maxWidth,
                )
            }

        exception.message shouldBe "'max-width' must be greater than 0"
    }

    @Test
    fun `ImagePreProcessingProperties maxHeight can be null`() {
        shouldNotThrowAny {
            createImagePreProcessingProperties(
                maxWidth = 100,
                maxHeight = null,
            )
        }
    }

    @Test
    fun `ImagePreProcessingProperties maxWidth can be null`() {
        shouldNotThrowAny {
            createImagePreProcessingProperties(
                maxWidth = null,
                maxHeight = 100,
            )
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [-1, 151])
    fun `blur cannot be outside bounds`(blur: Int) {
        shouldThrow<IllegalArgumentException> {
            createImagePreProcessingProperties(
                blur = blur,
            )
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 101])
    fun `quality cannot be outside bounds`(quality: Int) {
        shouldThrow<IllegalArgumentException> {
            createImagePreProcessingProperties(
                quality = quality,
            )
        }
    }

    @Test
    fun `pad cannot be negative`() {
        shouldThrow<IllegalArgumentException> {
            createImagePreProcessingProperties(
                pad = -1,
            )
        }
    }

    @Test
    fun `PreProcessingProperties default contains default values`() {
        val default = ImagePreProcessingProperties.DEFAULT
        default.format shouldBe null
        default.maxWidth shouldBe null
        default.maxHeight shouldBe null
        default.width shouldBe null
        default.height shouldBe null
        default.rotate shouldBe Rotate.default
        default.flip shouldBe Flip.default
        default.blur shouldBe null
        default.quality shouldBe null
        default.gravity shouldBe Gravity.default
        default.pad shouldBe null
        default.background shouldBe null
    }

    @Test
    fun `toRequestedImageTransformation uses the width over the maxWidth if supplied`() {
        val properties =
            shouldNotThrowAny {
                createImagePreProcessingProperties(
                    maxWidth = 100,
                    width = 200,
                )
            }
        properties.requestedImageTransformation.width shouldBe 200
    }

    @Test
    fun `toRequestedImageTransformation uses the height over the maxHeight if supplied`() {
        val properties =
            shouldNotThrowAny {
                createImagePreProcessingProperties(
                    maxHeight = 100,
                    height = 200,
                )
            }
        properties.requestedImageTransformation.height shouldBe 200
    }

    @Test
    fun `toRequestedImageTransformation canUpscale is false if maxWidth is true`() {
        val properties =
            shouldNotThrowAny {
                createImagePreProcessingProperties(
                    maxWidth = 100,
                )
            }
        properties.requestedImageTransformation.canUpscale shouldBe false
    }

    @Test
    fun `toRequestedImageTransformation canUpscale is false if maxHeight is true`() {
        val properties =
            shouldNotThrowAny {
                createImagePreProcessingProperties(
                    maxHeight = 100,
                )
            }
        properties.requestedImageTransformation.canUpscale shouldBe false
    }

    @Test
    fun `toRequestedImageTransformation canUpscale is true if maxHeight and maxWidth are null`() {
        val properties =
            shouldNotThrowAny {
                createImagePreProcessingProperties(
                    width = 200,
                )
            }
        properties.requestedImageTransformation.canUpscale shouldBe true
        properties.enabled
    }
}
