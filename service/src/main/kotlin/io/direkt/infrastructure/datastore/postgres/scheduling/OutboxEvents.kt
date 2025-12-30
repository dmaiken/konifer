package io.direkt.infrastructure.datastore.postgres.scheduling

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

sealed interface OutboxEvent {
    val eventType: String
}

@Serializable
data class ReapVariantEvent(
    val objectStoreBucket: String,
    val objectStoreKey: String,
) : OutboxEvent {
    companion object {
        const val TYPE = "REAP_VARIANT"
    }

    @Transient
    override val eventType: String = TYPE
}
