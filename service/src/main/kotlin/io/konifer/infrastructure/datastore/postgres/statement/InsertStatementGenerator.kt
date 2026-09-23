package io.konifer.infrastructure.datastore.postgres.statement

import com.github.f4b6a3.uuid.UuidCreator
import io.konifer.domain.asset.Asset
import io.konifer.domain.asset.AssetId
import io.konifer.domain.asset.AssetLabels
import io.konifer.domain.asset.AssetTags
import io.konifer.domain.variant.Variant
import io.konifer.infrastructure.datastore.postgres.LtreePathAdapter
import io.konifer.infrastructure.datastore.postgres.VariantParameterGenerator
import io.konifer.infrastructure.datastore.postgres.postgresJson
import konifer.jooq.tables.records.AssetLabelRecord
import konifer.jooq.tables.records.AssetTagRecord
import konifer.jooq.tables.records.AssetTreeRecord
import konifer.jooq.tables.records.AssetVariantRecord
import konifer.jooq.tables.references.ASSET_LABEL
import konifer.jooq.tables.references.ASSET_TAG
import konifer.jooq.tables.references.ASSET_TREE
import konifer.jooq.tables.references.ASSET_VARIANT
import org.jooq.DSLContext
import org.jooq.InsertOnDuplicateStep
import org.jooq.InsertResultStep
import org.jooq.JSONB
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

object InsertStatementGenerator {
    context(trx: DSLContext)
    fun insertAsset(
        asset: Asset.Pending,
        createdAt: LocalDateTime,
    ): InsertResultStep<AssetTreeRecord> {
        val treePath = LtreePathAdapter.toTreePathFromUriPath(asset.path)
        val insert =
            trx
                .insertInto(ASSET_TREE)
                .set(ASSET_TREE.ID, asset.id.value)
                .set(ASSET_TREE.PATH, treePath)
                .set(ASSET_TREE.ALT, asset.alt?.value)
                .set(ASSET_TREE.SOURCE, asset.source.toString())
                .set(ASSET_TREE.CREATED_AT, createdAt)
                .set(ASSET_TREE.MODIFIED_AT, createdAt)
        asset.externalSourceAddress?.let {
            insert.set(ASSET_TREE.SOURCE_URL, it)
        }

        return insert.returning()
    }

    context(trx: DSLContext)
    fun insertLabels(
        assetId: AssetId,
        labels: AssetLabels,
        dateTime: LocalDateTime = LocalDateTime.now(UTC),
    ): InsertOnDuplicateStep<AssetLabelRecord>? {
        if (labels.isEmpty()) return null

        val step =
            trx.insertInto(
                ASSET_LABEL,
                ASSET_LABEL.ID,
                ASSET_LABEL.ASSET_ID,
                ASSET_LABEL.LABEL_KEY,
                ASSET_LABEL.LABEL_VALUE,
                ASSET_LABEL.CREATED_AT,
            )
        labels.asMap().forEach { (key, value) ->
            step.values(UuidCreator.getTimeOrderedEpoch(), assetId.value, key, value, dateTime)
        }

        return step
    }

    context(trx: DSLContext)
    fun insertTags(
        assetId: AssetId,
        tags: AssetTags,
        dateTime: LocalDateTime = LocalDateTime.now(UTC),
    ): InsertOnDuplicateStep<AssetTagRecord>? {
        if (tags.isEmpty()) return null

        val step =
            trx.insertInto(
                ASSET_TAG,
                ASSET_TAG.ID,
                ASSET_TAG.ASSET_ID,
                ASSET_TAG.TAG_VALUE,
                ASSET_TAG.CREATED_AT,
            )
        tags.asSet().forEach { value ->
            step.values(UuidCreator.getTimeOrderedEpoch(), assetId.value, value, dateTime)
        }

        return step
    }

    context(trx: DSLContext)
    fun insertVariant(variant: Variant): InsertResultStep<AssetVariantRecord> {
        val transformations =
            if (variant.isOriginalVariant) {
                VariantParameterGenerator.generateImageVariantTransformations(variant.attributes)
            } else {
                VariantParameterGenerator.generateImageVariantTransformations(variant.transformation)
            }
        val attributes = VariantParameterGenerator.generateImageVariantAttributes(variant.attributes)
        val lqip = postgresJson.encodeToString(variant.lqips)
        return trx
            .insertInto(ASSET_VARIANT)
            .set(ASSET_VARIANT.ID, UuidCreator.getTimeOrderedEpoch())
            .set(ASSET_VARIANT.ASSET_ID, variant.assetId.value)
            .set(ASSET_VARIANT.OBJECT_STORE_BUCKET, variant.objectStoreBucket)
            .set(ASSET_VARIANT.OBJECT_STORE_KEY, variant.objectStoreKey)
            .set(ASSET_VARIANT.ATTRIBUTES, JSONB.valueOf(attributes))
            .set(ASSET_VARIANT.TRANSFORMATION, JSONB.valueOf(transformations))
            .set(ASSET_VARIANT.LQIP, JSONB.valueOf(lqip))
            .set(ASSET_VARIANT.ORIGINAL_VARIANT, variant.isOriginalVariant)
            .set(ASSET_VARIANT.CREATED_AT, variant.createdAt)
            .set(ASSET_VARIANT.EXPIRES_AT, variant.expiresAt)
            .set(ASSET_VARIANT.ACCESS_SCORE, 1.0)
            .set(ASSET_VARIANT.ACCESS_SCORE_AS_OF, variant.createdAt)
            .returning()
    }
}
