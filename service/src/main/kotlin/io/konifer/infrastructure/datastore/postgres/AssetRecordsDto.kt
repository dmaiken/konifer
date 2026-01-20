package io.konifer.infrastructure.datastore.postgres

import konifer.jooq.tables.records.AssetLabelRecord
import konifer.jooq.tables.records.AssetTagRecord
import konifer.jooq.tables.records.AssetTreeRecord
import konifer.jooq.tables.records.AssetVariantRecord

data class AssetRecordsDto(
    val asset: AssetTreeRecord,
    val variants: List<AssetVariantRecord>,
    val labels: List<AssetLabelRecord>,
    val tags: List<AssetTagRecord>,
)
