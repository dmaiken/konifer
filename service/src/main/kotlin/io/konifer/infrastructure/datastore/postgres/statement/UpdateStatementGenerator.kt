package io.konifer.infrastructure.datastore.postgres.statement

import io.konifer.domain.asset.Asset
import io.konifer.domain.variant.Variant
import konifer.jooq.tables.records.AssetTreeRecord
import konifer.jooq.tables.records.AssetVariantRecord
import konifer.jooq.tables.references.ASSET_TREE
import konifer.jooq.tables.references.ASSET_VARIANT
import org.jooq.DSLContext
import org.jooq.UpdateConditionStep

object UpdateStatementGenerator {
    context(trx: DSLContext)
    fun updateAsset(asset: Asset.Ready): UpdateConditionStep<AssetTreeRecord> =
        trx
            .update(ASSET_TREE)
            .set(ASSET_TREE.ALT, asset.alt?.value)
            .where(ASSET_TREE.ID.eq(asset.id.value))

    context(trx: DSLContext)
    fun updateModifiedAt(asset: Asset.Ready): UpdateConditionStep<AssetTreeRecord> =
        trx
            .update(ASSET_TREE)
            .set(ASSET_TREE.MODIFIED_AT, asset.modifiedAt)
            .where(ASSET_TREE.ID.eq(asset.id.value))

    context(trx: DSLContext)
    fun updateAssetReady(asset: Asset.Ready): UpdateConditionStep<AssetTreeRecord> =
        trx
            .update(ASSET_TREE)
            .set(ASSET_TREE.IS_READY, true)
            .set(ASSET_TREE.MODIFIED_AT, asset.modifiedAt)
            .where(ASSET_TREE.ID.eq(asset.id.value))

    context(trx: DSLContext)
    fun updateVariantUploaded(variant: Variant): UpdateConditionStep<AssetVariantRecord> =
        trx
            .update(ASSET_VARIANT)
            .set(ASSET_VARIANT.UPLOADED_AT, variant.uploadedAt)
            .set(ASSET_VARIANT.LAST_ACCESSED_AT, variant.uploadedAt)
            .where(ASSET_VARIANT.ID.eq(variant.id.value))
}
