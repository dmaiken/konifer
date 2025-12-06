package io.direkt.domain.aggregate

import io.direkt.domain.image.Attributes
import io.direkt.domain.image.LQIPs
import io.direkt.domain.image.Transformation
import java.time.LocalDateTime
import java.util.UUID

@JvmInline value class VariantId(
    val value: UUID,
)

sealed interface Variant {
    val id: VariantId
    val objectStoreBucket: String
    val objectStoreKey: String
    val isOriginalVariant: Boolean
    val attributes: Attributes
    val transformation: Transformation
    val transformationKey: Long
    val lqip: LQIPs
    val createdAt: LocalDateTime
    val uploadedAt: LocalDateTime?

    class New private constructor(
        override val id: VariantId,
        override val objectStoreBucket: String,
        override val objectStoreKey: String,
        override val isOriginalVariant: Boolean,
        override val attributes: Attributes,
        override val transformation: Transformation,
        override val transformationKey: Long,
        override val lqip: LQIPs,
        override val createdAt: LocalDateTime,
        override val uploadedAt: LocalDateTime?,
    ) : Variant {
        companion object {
            fun originalVariant(
                attributes: Attributes,
                objectStoreBucket: String,
                objectStoreKey: String,
                lqip: LQIPs,
            ): New =
                New(
                    id = VariantId(UUID.randomUUID()),
                    objectStoreBucket = objectStoreBucket,
                    objectStoreKey = objectStoreKey,
                    isOriginalVariant = true,
                    attributes = attributes,
                    transformation = Transformation.ORIGINAL_VARIANT,
                    transformationKey = 1234L,
                    lqip = lqip,
                    createdAt = LocalDateTime.now(),
                    uploadedAt = null,
                )
        }
    }
}
