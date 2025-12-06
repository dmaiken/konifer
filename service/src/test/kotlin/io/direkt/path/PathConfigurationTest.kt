package io.direkt.path

import io.direkt.image.model.ImageProperties
import io.direkt.image.model.PreProcessingProperties
import io.direkt.path.configuration.PathConfiguration
import io.direkt.infrastructure.s3.S3PathProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
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
                            preProcessing = PreProcessingProperties.DEFAULT,
                            lqip = setOf(),
                        ),
                    eagerVariants = emptyList(),
                    s3PathProperties = S3PathProperties.DEFAULT,
                )
            }

        exception.message shouldBe "not/supported is not a supported content type"
    }
}
