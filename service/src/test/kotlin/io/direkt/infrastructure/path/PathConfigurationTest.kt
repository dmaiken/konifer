package io.direkt.infrastructure.path

import io.direkt.domain.image.ImageProperties
import io.direkt.domain.image.PreProcessingProperties
import io.direkt.domain.path.PathConfiguration
import io.direkt.infrastructure.objectstore.s3.S3PathProperties
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
