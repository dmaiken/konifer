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
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val labels: Map<String, String>,
) {
    companion object {
        fun from(
            record: AssetTreeRecord,
            labels: Map<String, String>,
        ): Asset =
            Asset(
                id = checkNotNull(record.id),
                alt = record.alt,
                entryId = checkNotNull(record.entryId),
                path = checkNotNull(record.path).toPath(),
                createdAt = checkNotNull(record.createdAt),
                labels = labels,
            )
    }
}
