package io.konifer.domain.asset

import com.github.f4b6a3.uuid.UuidCreator
import io.konifer.common.asset.AssetSource
import io.konifer.common.http.StoreAssetRequest
import io.konifer.domain.variant.Variant
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import java.util.UUID

@JvmInline value class AssetId(
    val value: UUID = UuidCreator.getTimeOrderedEpoch(),
)

sealed interface Asset {
    val id: AssetId
    val path: String
    val entryId: Long?
    val alt: AssetAlt?
    val labels: AssetLabels
    val tags: AssetTags
    val source: AssetSource
    val sourceUrl: String?
    val createdAt: LocalDateTime
    val modifiedAt: LocalDateTime
    val isReady: Boolean
    val variants: MutableList<Variant>

    val descriptor: String
        get() = "$path:$entryId"

    class New private constructor(
        override val id: AssetId,
        override val path: String,
        override val entryId: Long?,
        override val alt: AssetAlt?,
        override val labels: AssetLabels,
        override val tags: AssetTags,
        override val source: AssetSource,
        override val sourceUrl: String?,
        override val createdAt: LocalDateTime,
        override val modifiedAt: LocalDateTime,
        override val isReady: Boolean,
        override val variants: MutableList<Variant>,
    ) : Asset {
        companion object {
            fun fromHttpRequest(
                path: String,
                request: StoreAssetRequest,
            ): New {
                val now = LocalDateTime.now(UTC)
                return New(
                    id = AssetId(),
                    path = path,
                    entryId = null,
                    alt = request.alt?.toAssetAlt(),
                    labels = request.labels.toAssetLabels(),
                    tags = request.tags.toAssetTags(),
                    source =
                        request.url?.let {
                            AssetSource.URL
                        } ?: AssetSource.UPLOAD,
                    sourceUrl = request.url,
                    createdAt = now,
                    modifiedAt = now,
                    isReady = false,
                    variants = mutableListOf(),
                )
            }
        }

        init {
            check(entryId == null)
            check(variants.isEmpty())
        }

        fun markPending(
            originalVariant: Variant,
            additionalLabels: AssetLabels,
        ): Pending {
            check(originalVariant is Variant.Pending) { "Variant must be in a pending state" }

            return Pending.fromNew(
                new = this,
                originalVariant = originalVariant,
                additionalLabels = additionalLabels,
            )
        }
    }

    class Pending private constructor(
        override val id: AssetId,
        override val path: String,
        override val entryId: Long?,
        override val alt: AssetAlt?,
        override val labels: AssetLabels,
        override val tags: AssetTags,
        override val source: AssetSource,
        override val sourceUrl: String?,
        override val createdAt: LocalDateTime,
        override val modifiedAt: LocalDateTime,
        override val isReady: Boolean,
        override val variants: MutableList<Variant>,
    ) : Asset {
        companion object {
            fun fromNew(
                new: New,
                originalVariant: Variant,
                additionalLabels: AssetLabels = AssetLabels.empty,
            ): Pending =
                Pending(
                    id = new.id,
                    path = new.path.removeSuffix("/"),
                    entryId = null,
                    alt = new.alt,
                    labels = new.labels.merge(additionalLabels),
                    tags = new.tags,
                    source = new.source,
                    sourceUrl = new.sourceUrl,
                    createdAt = new.createdAt,
                    modifiedAt = new.modifiedAt,
                    isReady = false,
                    variants = mutableListOf(originalVariant),
                )
        }

        init {
            check(entryId == null)
            check(variants.size == 1)
        }
    }

    class PendingPersisted(
        override val id: AssetId,
        override val path: String,
        override val entryId: Long?,
        override val alt: AssetAlt?,
        override val labels: AssetLabels,
        override val tags: AssetTags,
        override val source: AssetSource,
        override val sourceUrl: String?,
        override val createdAt: LocalDateTime,
        override val modifiedAt: LocalDateTime,
        override val isReady: Boolean,
        override val variants: MutableList<Variant>,
    ) : Asset {
        init {
            checkNotNull(entryId)
            check(variants.size == 1)
            check(variants[0] is Variant.Pending)
            check(variants[0].isOriginalVariant)
        }

        fun markReady(uploadedAt: LocalDateTime): Ready =
            Ready.fromPendingPersisted(
                persisted = this,
                originalVariant = (variants.first() as Variant.Pending).markReady(uploadedAt),
            )
    }

    class Ready(
        override val id: AssetId,
        override val path: String,
        override val entryId: Long?,
        override val alt: AssetAlt?,
        override val labels: AssetLabels,
        override val tags: AssetTags,
        override val source: AssetSource,
        override val sourceUrl: String?,
        override val createdAt: LocalDateTime,
        override val modifiedAt: LocalDateTime,
        override val isReady: Boolean,
        override val variants: MutableList<Variant>,
    ) : Asset {
        init {
            checkNotNull(entryId)
            check(variants.isNotEmpty())
            check(isReady)
        }

        companion object {
            fun fromPendingPersisted(
                persisted: PendingPersisted,
                originalVariant: Variant,
            ): Ready =
                Ready(
                    id = persisted.id,
                    path = persisted.path,
                    entryId = persisted.entryId,
                    alt = persisted.alt,
                    labels = persisted.labels,
                    tags = persisted.tags,
                    source = persisted.source,
                    sourceUrl = persisted.sourceUrl,
                    createdAt = persisted.createdAt,
                    modifiedAt = LocalDateTime.now(UTC),
                    isReady = true,
                    variants = mutableListOf(originalVariant),
                )

            fun from(assetData: AssetData): Ready =
                Ready(
                    id = assetData.id,
                    path = assetData.path,
                    entryId = assetData.entryId,
                    alt = assetData.alt?.toAssetAlt(),
                    labels = assetData.labels.toAssetLabels(),
                    tags = assetData.tags.toAssetTags(),
                    source = assetData.source,
                    sourceUrl = assetData.sourceUrl,
                    createdAt = assetData.createdAt,
                    modifiedAt = assetData.modifiedAt,
                    isReady = true,
                    variants =
                        assetData.variants
                            .map { variantData ->
                                if (variantData.uploadedAt != null) {
                                    Variant.Ready.from(assetData.id, variantData)
                                } else {
                                    Variant.Pending.from(assetData.id, variantData)
                                }
                            }.toMutableList(),
                )
        }

        fun update(
            alt: String?,
            labels: Map<String, String>,
            tags: Set<String>,
        ): Ready =
            Ready(
                id = id,
                path = path,
                entryId = entryId,
                alt = alt?.toAssetAlt(),
                labels = labels.toAssetLabels(),
                tags = tags.toAssetTags(),
                source = source,
                sourceUrl = sourceUrl,
                createdAt = createdAt,
                modifiedAt = LocalDateTime.now(UTC),
                isReady = isReady,
                variants = variants,
            )
    }
}
