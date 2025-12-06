package io.direkt.domain.variant

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
    val lqips: LQIPs
    val createdAt: LocalDateTime
    val uploadedAt: LocalDateTime?

    class Pending(
        override val id: VariantId,
        override val objectStoreBucket: String,
        override val objectStoreKey: String,
        override val isOriginalVariant: Boolean,
        override val attributes: Attributes,
        override val transformation: Transformation,
        override val transformationKey: Long,
        override val lqips: LQIPs,
        override val createdAt: LocalDateTime,
        override val uploadedAt: LocalDateTime?,
    ) : Variant {
        init {
            check(uploadedAt == null)
        }

        companion object {
            fun originalVariant(
                attributes: Attributes,
                objectStoreBucket: String,
                objectStoreKey: String,
                lqip: LQIPs,
            ): Pending =
                Pending(
                    id = VariantId(UUID.randomUUID()),
                    objectStoreBucket = objectStoreBucket,
                    objectStoreKey = objectStoreKey,
                    isOriginalVariant = true,
                    attributes = attributes,
                    transformation = Transformation.ORIGINAL_VARIANT,
                    transformationKey = 1234L,
                    lqips = lqip,
                    createdAt = LocalDateTime.now(),
                    uploadedAt = null,
                )
        }

        fun markUploaded(uploadedAt: LocalDateTime): Uploaded = Uploaded.fromPending(
            pending = this,
            uploadedAt = uploadedAt,
        )
    }

    class Uploaded private constructor(
        override val id: VariantId,
        override val objectStoreBucket: String,
        override val objectStoreKey: String,
        override val isOriginalVariant: Boolean,
        override val attributes: Attributes,
        override val transformation: Transformation,
        override val transformationKey: Long,
        override val lqips: LQIPs,
        override val createdAt: LocalDateTime,
        override val uploadedAt: LocalDateTime?,
    ) : Variant {
        init {
            checkNotNull(uploadedAt)
        }

        companion object {
            fun fromPending(pending: Pending, uploadedAt: LocalDateTime): Uploaded = Uploaded(
                id = pending.id,
                objectStoreBucket = pending.objectStoreBucket,
                objectStoreKey = pending.objectStoreKey,
                isOriginalVariant = pending.isOriginalVariant,
                attributes = pending.attributes,
                transformation = pending.transformation,
                transformationKey = pending.transformationKey,
                lqips = pending.lqips,
                createdAt = pending.createdAt,
                uploadedAt = uploadedAt,
            )
        }
    }
}
