package io.konifer.infrastructure.path

import io.konifer.domain.image.ImageProperties
import io.konifer.domain.path.CacheControlProperties
import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.variant.preprocessing.PreProcessingProperties
import io.konifer.infrastructure.objectstore.ObjectStoreProperties
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
