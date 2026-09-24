package io.konifer.domain.variant

import io.konifer.domain.variant.retention.CacheProperties
import io.konifer.domain.variant.retention.RetentionProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TransformPropertiesTest {
    @Test
    fun `eager variant amount cannot exceed max-variants`() {
        shouldThrow<IllegalArgumentException> {
            TransformProperties(
                eagerVariants = listOf("small", "medium", "large"),
                retention =
                    RetentionProperties(
                        cache =
                            CacheProperties(
                                maxVariants = 2,
                            ),
                    ),
            )
        }.message shouldBe "max-variants (2) cannot be less than number of eager-variants (3) defined for path"
    }
}
