package io.konifer.domain.variant

import com.github.f4b6a3.uuid.UuidCreator
import io.konifer.domain.asset.AssetId
import io.konifer.domain.transformation.Transformation
import io.konifer.domain.variant.attribute.Attributes
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import java.util.UUID

@JvmInline value class VariantId(
    val value: UUID = UuidCreator.getTimeOrderedEpoch(),
)

sealed interface Variant {
    val id: VariantId
    val assetId: AssetId
    val objectStoreBucket: String
    val objectStoreKey: String
    val isOriginalVariant: Boolean
        get() = specification == VariantSpecification.Original
    val attributes: Attributes
    val specification: VariantSpecification
    val lqips: LQIPs
    val createdAt: LocalDateTime
    val uploadedAt: LocalDateTime?
    val expiresAt: LocalDateTime?

    class Pending(
        override val id: VariantId,
        override val assetId: AssetId,
        override val objectStoreBucket: String,
        override val objectStoreKey: String,
        override val attributes: Attributes,
        override val specification: VariantSpecification,
        override val lqips: LQIPs,
        override val createdAt: LocalDateTime,
        override val uploadedAt: LocalDateTime? = null,
        override val expiresAt: LocalDateTime?,
    ) : Variant {
        init {
            check(uploadedAt == null)
            // Original variant cannot expire
            if (isOriginalVariant) {
                check(expiresAt == null)
            }
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
                    id = VariantId(),
                    assetId = assetId,
                    objectStoreBucket = objectStoreBucket,
                    objectStoreKey = objectStoreKey,
                    attributes = attributes,
                    specification = VariantSpecification.Original,
                    lqips = lqip,
                    createdAt = LocalDateTime.now(UTC),
                    expiresAt = null,
                )

            fun newVariant(
                assetId: AssetId,
                attributes: Attributes,
                transformation: Transformation,
                objectStoreBucket: String,
                objectStoreKey: String,
                lqip: LQIPs,
                expiresAt: LocalDateTime?,
            ): Pending =
                Pending(
                    id = VariantId(),
                    assetId = assetId,
                    objectStoreBucket = objectStoreBucket,
                    objectStoreKey = objectStoreKey,
                    attributes = attributes,
                    specification = VariantSpecification.Transformed(transformation),
                    lqips = lqip,
                    createdAt = LocalDateTime.now(UTC),
                    expiresAt = expiresAt,
                )

            fun from(
                assetId: AssetId,
                variantData: VariantData,
            ): Pending =
                Pending(
                    id = variantData.id,
                    assetId = assetId,
                    objectStoreBucket = variantData.objectStoreBucket,
                    objectStoreKey = variantData.objectStoreKey,
                    attributes = variantData.attributes,
                    specification = variantData.specification,
                    lqips = variantData.lqips,
                    createdAt = variantData.createdAt,
                    uploadedAt = variantData.uploadedAt,
                    expiresAt = variantData.expiresAt,
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
        override val attributes: Attributes,
        override val specification: VariantSpecification,
        override val lqips: LQIPs,
        override val createdAt: LocalDateTime,
        override val uploadedAt: LocalDateTime?,
        override val expiresAt: LocalDateTime?,
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
                    attributes = pending.attributes,
                    specification = pending.specification,
                    lqips = pending.lqips,
                    createdAt = pending.createdAt,
                    uploadedAt = uploadedAt,
                    expiresAt = pending.expiresAt,
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
                    attributes = variantData.attributes,
                    specification = variantData.specification,
                    lqips = variantData.lqips,
                    createdAt = variantData.createdAt,
                    uploadedAt = variantData.uploadedAt,
                    expiresAt = variantData.expiresAt,
                )
        }
    }
}
