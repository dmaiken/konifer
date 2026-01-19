package io.direkt.infrastructure.path

import io.direkt.domain.image.ImageProperties
import io.direkt.domain.path.CacheControlProperties
import io.direkt.domain.path.PathConfiguration
import io.direkt.domain.variant.preprocessing.PreProcessingProperties
import io.direkt.infrastructure.objectstore.ObjectStoreProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PathConfigurationTest {
    @Test
    fun `unsupported content type is not allowed`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                PathConfiguration(
                    allowedContentTypes = listOf("not/supported"),
                    image =
                        ImageProperties(
                            previews = setOf(),
                        ),
                    eagerVariants = emptyList(),
                    objectStore = ObjectStoreProperties.DEFAULT,
                    preProcessing = PreProcessingProperties.DEFAULT,
                    cacheControl = CacheControlProperties.DEFAULT,
                )
            }

        exception.message shouldBe "not/supported is not a supported content type"
    }
}
