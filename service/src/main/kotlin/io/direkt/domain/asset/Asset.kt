package io.direkt.domain.asset

import com.github.f4b6a3.uuid.UuidCreator
import io.direkt.domain.variant.Variant
import io.direkt.infrastructure.StoreAssetRequest
import java.time.LocalDateTime
import java.util.UUID

@JvmInline value class AssetId(
    val value: UUID = UuidCreator.getTimeOrderedEpoch(),
)

sealed interface Asset {
    val id: AssetId
    val path: String
    val entryId: Long?
    val alt: String?
    val labels: Map<String, String>
    val tags: Set<String>
    val source: AssetSource
    val sourceUrl: String?
    val createdAt: LocalDateTime
    val modifiedAt: LocalDateTime
    val isReady: Boolean
    val variants: MutableList<Variant>

    class New private constructor(
        override val id: AssetId,
        override val path: String,
        override val entryId: Long?,
        override val alt: String?,
        override val labels: Map<String, String>,
        override val tags: Set<String>,
        override val source: AssetSource,
        override val sourceUrl: String?,
        override val createdAt: LocalDateTime,
        override val modifiedAt: LocalDateTime,
        override val isReady: Boolean,
        override val variants: MutableList<Variant>,
    ) : Asset {
        companion object {
            /**
             * Looks like some assistive devices truncate alts after 125 characters
             */
            private const val MAX_ALT_LENGTH: Int = 125

            /**
             * Inspired from AWS limit
             */
            private const val MAX_LABEL_KEY_LENGTH: Int = 128

            /**
             * Inspired from AWS limit
             */
            private const val MAX_LABEL_VALUE_LENGTH: Int = 256

            /**
             * Inspired from AWS limit
             */
            private const val MAX_LABELS: Int = 50

            private const val MAX_TAG_VALUE_LENGTH: Int = 256

            fun fromHttpRequest(
                path: String,
                request: StoreAssetRequest,
            ): New {
                val now = LocalDateTime.now()
                return New(
                    id = AssetId(),
                    path = path,
                    entryId = null,
                    alt = request.alt,
                    labels = request.labels,
                    tags = request.tags,
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
            if (alt != null && alt.length > MAX_ALT_LENGTH) {
                throw IllegalArgumentException("Alt exceeds max length of $MAX_ALT_LENGTH")
            }
            if (labels.any { it.key.length > MAX_LABEL_KEY_LENGTH || it.value.length > MAX_LABEL_VALUE_LENGTH }) {
                throw IllegalArgumentException("Labels exceed max length of ($MAX_LABEL_KEY_LENGTH, $MAX_LABEL_VALUE_LENGTH)")
            }
            if (labels.size > MAX_LABELS) {
                throw IllegalArgumentException("Cannot have more than $MAX_LABELS labels")
            }
            if (tags.any { it.length > MAX_TAG_VALUE_LENGTH }) {
                throw IllegalArgumentException("Tags exceed max length of $MAX_TAG_VALUE_LENGTH")
            }
        }

        fun markPending(originalVariant: Variant): Pending {
            check(originalVariant is Variant.Pending) { "Variant must be in a pending state" }

            return Pending.fromNew(
                new = this,
                originalVariant = originalVariant,
            )
        }
    }

    class Pending private constructor(
        override val id: AssetId,
        override val path: String,
        override val entryId: Long?,
        override val alt: String?,
        override val labels: Map<String, String>,
        override val tags: Set<String>,
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
            ): Pending =
                Pending(
                    id = new.id,
                    path = new.path,
                    entryId = null,
                    alt = new.alt,
                    labels = new.labels,
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
        override val alt: String?,
        override val labels: Map<String, String>,
        override val tags: Set<String>,
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
        override val alt: String?,
        override val labels: Map<String, String>,
        override val tags: Set<String>,
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
                    modifiedAt = LocalDateTime.now(),
                    isReady = true,
                    variants = mutableListOf(originalVariant),
                )

            fun from(assetData: AssetData): Ready =
                Ready(
                    id = assetData.id,
                    path = assetData.path,
                    entryId = assetData.entryId,
                    alt = assetData.alt,
                    labels = assetData.labels,
                    tags = assetData.tags,
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
                alt = alt,
                labels = labels,
                tags = tags,
                source = source,
                sourceUrl = sourceUrl,
                createdAt = createdAt,
                modifiedAt = LocalDateTime.now(),
                isReady = isReady,
                variants = variants,
            )
    }
}
