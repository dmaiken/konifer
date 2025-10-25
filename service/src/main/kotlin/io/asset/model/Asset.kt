package io.asset.model

import direkt.jooq.tables.records.AssetTreeRecord
import io.asset.repository.toPath
import java.time.LocalDateTime
import java.util.UUID

data class Asset(
    val id: UUID = UUID.randomUUID(),
    val alt: String?,
    val path: String,
    val entryId: Long,
    val labels: Map<String, String>,
    val tags: Set<String>,
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        fun from(
            record: AssetTreeRecord,
            labels: Map<String, String>,
            tags: Set<String>,
        ): Asset =
            Asset(
                id = checkNotNull(record.id),
                alt = record.alt,
                entryId = checkNotNull(record.entryId),
                path = checkNotNull(record.path).toPath(),
                labels = labels,
                tags = tags,
                createdAt = checkNotNull(record.createdAt),
            )
    }
}
