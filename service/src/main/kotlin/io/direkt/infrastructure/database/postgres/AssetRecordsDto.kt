package io.direkt.infrastructure.database.postgres

import direkt.jooq.tables.records.AssetLabelRecord
import direkt.jooq.tables.records.AssetTagRecord
import direkt.jooq.tables.records.AssetTreeRecord
import direkt.jooq.tables.records.AssetVariantRecord

data class AssetRecordsDto(
    val asset: AssetTreeRecord,
    val variants: List<AssetVariantRecord>,
    val labels: List<AssetLabelRecord>,
    val tags: List<AssetTagRecord>,
)