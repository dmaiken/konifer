package io.direkt.domain.aggregate

import io.direkt.asset.handler.AssetSource
import io.direkt.asset.model.StoreAssetRequest
import java.time.LocalDateTime
import java.util.UUID

@JvmInline value class AssetId(
    val value: UUID,
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
    val variants: List<Variant>

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
        override val variants: List<Variant>,
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
                    id = AssetId(UUID.randomUUID()),
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
                    variants = emptyList(),
                )
            }
        }

        init {
            validate()
        }

        fun markPending(originalVariant: Variant): Asset {
            check(originalVariant is Variant.New) { "Variant must be in a new state" }

            return Pending.fromNew(
                new = this,
                originalVariant = originalVariant,
            )
        }

        private fun validate() {
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
        override val variants: List<Variant>,
    ) : Asset {
        companion object {
            fun fromNew(
                new: New,
                originalVariant: Variant,
            ): Pending =
                Pending(
                    id = new.id,
                    path = new.path,
                    entryId = new.entryId,
                    alt = new.alt,
                    labels = new.labels,
                    tags = new.tags,
                    source = new.source,
                    sourceUrl = new.sourceUrl,
                    createdAt = new.createdAt,
                    modifiedAt = new.modifiedAt,
                    isReady = false,
                    variants = listOf(originalVariant),
                )
        }

        init {
            checkNotNull(entryId)
            check(variants.size == 1)
        }
    }
}
