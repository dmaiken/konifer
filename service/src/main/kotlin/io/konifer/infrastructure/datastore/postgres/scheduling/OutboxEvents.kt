package io.konifer.infrastructure.datastore.postgres.scheduling

import direkt.jooq.tables.records.AssetVariantRecord
import direkt.jooq.tables.references.ASSET_VARIANT
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jooq.CommonTableExpression
import org.jooq.Field
import org.jooq.JSONB
import org.jooq.impl.DSL.function
import org.jooq.impl.DSL.inline

sealed interface OutboxEvent {
    val eventType: String
}

@Serializable
data class VariantDeletedEvent(
    val objectStoreBucket: String,
    val objectStoreKey: String,
) : OutboxEvent {
    companion object {
        const val TYPE = "VARIANT_DELETED"

        fun jsonJooqFunction(deleteCte: CommonTableExpression<AssetVariantRecord>): Field<JSONB> =
            function(
                "json_build_object",
                JSONB::class.java,
                inline("objectStoreBucket"),
                deleteCte.field(ASSET_VARIANT.OBJECT_STORE_BUCKET),
                inline("objectStoreKey"),
                deleteCte.field(ASSET_VARIANT.OBJECT_STORE_KEY),
            )
    }

    @Transient
    override val eventType: String = TYPE
}
