package io.path

import image.model.ImageProperties
import image.model.PreProcessingProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.path.configuration.PathConfiguration
import org.junit.jupiter.api.Test

class PathConfigurationTest {
    @Test
    fun `unsupported content type is not allowed`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                PathConfiguration.create(
                    allowedContentTypes = listOf("not/supported"),
                    imageProperties =
                        ImageProperties.create(
                            PreProcessingProperties.create(
                                maxWidth = null,
                                maxHeight = null,
                                imageFormat = null,
                            ),
                        ),
                )
            }

        exception.message shouldBe "not/supported is not a supported content type"
    }
}
