package io.konifer.infrastructure.path

import io.konifer.domain.path.PathConfiguration
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
                )
            }

        exception.message shouldBe "not/supported is not a supported content type"
    }
}
