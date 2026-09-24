package io.konifer.infrastructure.datastore.postgres.statement

import io.konifer.domain.asset.AssetId
import io.konifer.domain.variant.VariantId
import io.konifer.domain.variant.retention.CacheProperties
import io.konifer.infrastructure.datastore.postgres.statement.FieldStatementGenerator.currentUtcLocalDateTime
import konifer.jooq.tables.references.ASSET_VARIANT
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

object VariantEvictionHelper {
    context(trx: DSLContext)
    suspend fun evictIfNecessary(
        assetId: AssetId,
        uploadedVariantId: VariantId,
        cacheProperties: CacheProperties,
    ) {
        val targets =
            trx
                .select(ASSET_VARIANT.ID)
                .from(ASSET_VARIANT)
                .where(ASSET_VARIANT.ASSET_ID.eq(assetId.value))
                .and(ASSET_VARIANT.ID.ne(uploadedVariantId.value))
                .and(ASSET_VARIANT.ORIGINAL_VARIANT.eq(false))
                .and(ASSET_VARIANT.UPLOADED_AT.isNotNull)
                .orderBy(
                    effectiveAccessScore(cacheProperties).desc(),
                    ASSET_VARIANT.LAST_ACCESSED_AT.desc().nullsLast(),
                    ASSET_VARIANT.CREATED_AT.desc(),
                    ASSET_VARIANT.ID.desc(),
                ).offset(cacheProperties.maxVariants - 1)

        DeleteStatementGenerator
            .deleteTargetVariantsAndEnqueueOutbox(targets)
            .awaitFirstOrNull()
    }

    private fun effectiveAccessScore(cacheProperties: CacheProperties): Field<Double?> {
        val scoreAsOf = currentUtcLocalDateTime()
        val elapsedSeconds =
            DSL.field(
                "extract(epoch from ({0} - {1}))",
                SQLDataType.DOUBLE,
                scoreAsOf,
                ASSET_VARIANT.ACCESS_SCORE_AS_OF,
            )
        val halfLifeSeconds = cacheProperties.accessScoreHalfLife.inWholeMilliseconds / 1_000.0
        val decay =
            DSL
                .power(
                    0.5,
                    DSL.greatest(DSL.inline(0.0), elapsedSeconds).div(halfLifeSeconds),
                ).cast(Double::class.java)

        return ASSET_VARIANT.ACCESS_SCORE.mul(decay)
    }
}
