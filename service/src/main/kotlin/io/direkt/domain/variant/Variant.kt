package io.direkt.domain.variant

import io.direkt.domain.asset.AssetId
import java.time.LocalDateTime
import java.util.UUID

@JvmInline value class VariantId(
    val value: UUID,
)

sealed interface Variant {
    val id: VariantId
    val assetId: AssetId
    val objectStoreBucket: String
    val objectStoreKey: String
    val isOriginalVariant: Boolean
    val attributes: Attributes
    val transformation: Transformation
    val lqips: LQIPs
    val createdAt: LocalDateTime
    val uploadedAt: LocalDateTime?

    class Pending(
        override val id: VariantId,
        override val assetId: AssetId,
        override val objectStoreBucket: String,
        override val objectStoreKey: String,
        override val isOriginalVariant: Boolean,
        override val attributes: Attributes,
        override val transformation: Transformation,
        override val lqips: LQIPs,
        override val createdAt: LocalDateTime,
        override val uploadedAt: LocalDateTime?,
    ) : Variant {
        init {
            check(uploadedAt == null)
        }

        companion object {
            fun originalVariant(
                assetId: AssetId,
                attributes: Attributes,
                objectStoreBucket: String,
                objectStoreKey: String,
                lqip: LQIPs,
            ): Pending =
                Pending(
                    id = VariantId(UUID.randomUUID()),
                    assetId = assetId,
                    objectStoreBucket = objectStoreBucket,
                    objectStoreKey = objectStoreKey,
                    isOriginalVariant = true,
                    attributes = attributes,
                    transformation = Transformation.ORIGINAL_VARIANT,
                    lqips = lqip,
                    createdAt = LocalDateTime.now(),
                    uploadedAt = null,
                )

            fun newVariant(
                assetId: AssetId,
                attributes: Attributes,
                transformation: Transformation,
                objectStoreBucket: String,
                objectStoreKey: String,
                lqip: LQIPs,
            ): Pending =
                Pending(
                    id = VariantId(UUID.randomUUID()),
                    assetId = assetId,
                    objectStoreBucket = objectStoreBucket,
                    objectStoreKey = objectStoreKey,
                    isOriginalVariant = false,
                    attributes = attributes,
                    transformation = transformation,
                    lqips = lqip,
                    createdAt = LocalDateTime.now(),
                    uploadedAt = null,
                )
        }

        fun markReady(uploadedAt: LocalDateTime): Ready =
            Ready.fromPending(
                pending = this,
                uploadedAt = uploadedAt,
            )
    }

    class Ready(
        override val id: VariantId,
        override val assetId: AssetId,
        override val objectStoreBucket: String,
        override val objectStoreKey: String,
        override val isOriginalVariant: Boolean,
        override val attributes: Attributes,
        override val transformation: Transformation,
        override val lqips: LQIPs,
        override val createdAt: LocalDateTime,
        override val uploadedAt: LocalDateTime?,
    ) : Variant {
        init {
            checkNotNull(uploadedAt)
        }

        companion object {
            fun fromPending(
                pending: Pending,
                uploadedAt: LocalDateTime,
            ): Ready =
                Ready(
                    id = pending.id,
                    assetId = pending.assetId,
                    objectStoreBucket = pending.objectStoreBucket,
                    objectStoreKey = pending.objectStoreKey,
                    isOriginalVariant = pending.isOriginalVariant,
                    attributes = pending.attributes,
                    transformation = pending.transformation,
                    lqips = pending.lqips,
                    createdAt = pending.createdAt,
                    uploadedAt = uploadedAt,
                )

            fun from(
                assetId: AssetId,
                variantData: VariantData,
            ): Ready =
                Ready(
                    id = variantData.id,
                    assetId = assetId,
                    objectStoreBucket = variantData.objectStoreBucket,
                    objectStoreKey = variantData.objectStoreKey,
                    isOriginalVariant = variantData.isOriginalVariant,
                    attributes = variantData.attributes,
                    transformation = variantData.transformation,
                    lqips = variantData.lqips,
                    createdAt = variantData.createdAt,
                    uploadedAt = variantData.uploadedAt,
                )
        }
    }
}
