package io.konifer.infrastructure.datastore.postgres.projection

import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.VariantId
import io.konifer.domain.variant.VariantSpecification
import io.konifer.domain.variant.attribute.Attributes
import io.konifer.domain.variant.projection.VariantDeliveryProjection
import io.konifer.infrastructure.datastore.postgres.postgresJson
import io.konifer.infrastructure.datastore.postgres.schema.ImageVariantAttributes
import io.konifer.infrastructure.datastore.postgres.toVariantSpecificationOrNull
import konifer.jooq.tables.records.AssetVariantRecord

class PostgresVariantDeliveryProjection private constructor(
    private val variantRecord: AssetVariantRecord,
    override val specification: VariantSpecification,
) : VariantDeliveryProjection {
    companion object {
        fun from(variantRecord: AssetVariantRecord): PostgresVariantDeliveryProjection? =
            variantRecord.toVariantSpecificationOrNull()?.let { PostgresVariantDeliveryProjection(variantRecord, it) }
    }

    override val id: VariantId = VariantId(checkNotNull(variantRecord.id))

    override val objectStoreBucket: String
        get() = checkNotNull(variantRecord.objectStoreBucket)

    override val objectStoreKey: String
        get() = checkNotNull(variantRecord.objectStoreKey)

    override val attributes: Attributes by lazy(LazyThreadSafetyMode.NONE) {
        postgresJson
            .decodeFromString<ImageVariantAttributes>(
                checkNotNull(variantRecord.attributes).data(),
            ).toAttributes()
    }

    override val lqips: LQIPs by lazy(LazyThreadSafetyMode.NONE) {
        postgresJson
            .decodeFromString(
                checkNotNull(variantRecord.lqip).data(),
            )
    }

    override val isOriginalVariant: Boolean = checkNotNull(variantRecord.originalVariant)
}
