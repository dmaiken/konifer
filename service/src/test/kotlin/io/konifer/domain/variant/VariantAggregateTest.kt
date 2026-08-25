package io.konifer.domain.variant

import io.konifer.common.image.ImageFormat
import io.konifer.domain.asset.AssetId
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.transformation.toDimension
import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

class VariantAggregateTest {
    @Test
    fun `cannot create pending original variant with an expiration`() {
        shouldThrow<IllegalStateException> {
            Variant.Pending(
                id = VariantId(),
                assetId = AssetId(),
                objectStoreBucket = "bucket",
                objectStoreKey = "key",
                isOriginalVariant = true,
                attributes =
                    Attributes(
                        width = 10.toDimension(),
                        height = 10.toDimension(),
                        format = ImageFormat.PNG,
                        colorSpace = ColorSpace.SRGB,
                    ),
                transformation = Transformation.ORIGINAL_VARIANT,
                lqips = LQIPs.NONE,
                createdAt = LocalDateTime.now(UTC),
                uploadedAt = null,
                expiresAt = LocalDateTime.now(UTC).plusMinutes(10),
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
                isOriginalVariant = true,
                attributes =
                    Attributes(
                        width = 10.toDimension(),
                        height = 10.toDimension(),
                        format = ImageFormat.PNG,
                        colorSpace = ColorSpace.SRGB,
                    ),
                transformation = Transformation.ORIGINAL_VARIANT,
                lqips = LQIPs.NONE,
                createdAt = LocalDateTime.now(UTC),
                uploadedAt = LocalDateTime.now(UTC),
                expiresAt = null,
            )
        }
    }
}
