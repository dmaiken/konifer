package io.path

import image.model.ImageProperties
import image.model.PreProcessingProperties
import io.aws.S3Properties
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
                            preProcessing =
                                PreProcessingProperties.create(
                                    maxWidth = null,
                                    maxHeight = null,
                                    imageFormat = null,
                                ),
                            lqip = setOf(),
                        ),
                    eagerVariants = emptyList(),
                    s3Properties = S3Properties.DEFAULT,
                )
            }

        exception.message shouldBe "not/supported is not a supported content type"
    }
}
