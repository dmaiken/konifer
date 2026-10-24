package io.konifer.domain.variant

import io.konifer.common.image.ImageFormat
import io.konifer.domain.asset.AssetId
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.variant.VariantSpecification
import io.konifer.domain.variant.attribute.Attributes
import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test
import java.time.Instant

class VariantAggregateTest {
    @Test
    fun `cannot create pending original variant with an expiration`() {
        shouldThrow<IllegalStateException> {
            Variant.Pending(
                id = VariantId(),
                assetId = AssetId(),
                objectStoreBucket = "bucket",
                objectStoreKey = "key",
                attributes =
                    Attributes(
                        width = 10.toDimension(),
                        height = 10.toDimension(),
                        format = ImageFormat.PNG,
                        colorSpace = ColorSpace.SRGB,
                    ),
                specification = VariantSpecification.Original,
                lqips = LQIPs.NONE,
                createdAt = Instant.now(),
                uploadedAt = null,
                expiresAt = Instant.now().plus(10, java.time.temporal.ChronoUnit.MINUTES),
            )
        }
    }

    @Test
    fun `cannot create pending original variant with uploadedAt set`() {
        shouldThrow<IllegalStateException> {
            Variant.Pending(
                id = VariantId(),
                assetId = AssetId(),
                objectStoreBucket = "bucket",
                objectStoreKey = "key",
                attributes =
                    Attributes(
                        width = 10.toDimension(),
                        height = 10.toDimension(),
                        format = ImageFormat.PNG,
                        colorSpace = ColorSpace.SRGB,
                    ),
                specification = VariantSpecification.Original,
                lqips = LQIPs.NONE,
                createdAt = Instant.now(),
                uploadedAt = Instant.now(),
                expiresAt = null,
            )
        }
    }
}
