package io.konifer.infrastructure.datastore.postgres.projection

import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.VariantId
import io.konifer.domain.variant.VariantSpecification
import io.konifer.domain.variant.attribute.Attributes
import io.konifer.domain.variant.projection.VariantDeliveryProjection
import io.konifer.infrastructure.datastore.postgres.ImageVariantAttributes
import io.konifer.infrastructure.datastore.postgres.postgresJson
import io.konifer.infrastructure.datastore.postgres.toVariantSpecification
import konifer.jooq.tables.records.AssetVariantRecord

class PostgresVariantDeliveryProjection(
    private val variantRecord: AssetVariantRecord,
) : VariantDeliveryProjection {
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

    override val specification: VariantSpecification by lazy(LazyThreadSafetyMode.NONE) {
        variantRecord.toVariantSpecification()
    }

    override val isOriginalVariant: Boolean = checkNotNull(variantRecord.originalVariant)
}
