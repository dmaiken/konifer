package asset

import asset.repository.getNonNull
import io.asset.repository.toPath
import org.jooq.Record
import tessa.jooq.tables.AssetTree.Companion.ASSET_TREE
import java.time.LocalDateTime
import java.util.UUID

data class Asset(
    val id: UUID = UUID.randomUUID(),
    val alt: String?,
    val path: String,
    val entryId: Long,
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        fun from(record: Record): Asset =
            Asset(
                id = record.getNonNull(ASSET_TREE.ID),
                alt = record.getNonNull(ASSET_TREE.ALT),
                entryId = record.getNonNull(ASSET_TREE.ENTRY_ID),
                path = record.getNonNull(ASSET_TREE.PATH).toPath(),
                createdAt = record.getNonNull(ASSET_TREE.CREATED_AT),
            )
    }
}
