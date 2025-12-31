package io.direkt.infrastructure.datastore.postgres.scheduling

import direkt.jooq.tables.references.OUTBOX
import io.direkt.infrastructure.datastore.postgres.getNonNull
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.serialization.json.Json
import org.jooq.DSLContext
import reactor.core.publisher.Flux

suspend fun fetchOutboxReaperEvents(
    dslContext: DSLContext,
    expectAmount: Int,
): List<ReapVariantEvent> {
    val events =
        Flux
            .from(
                dslContext
                    .select()
                    .from(OUTBOX),
            ).asFlow()
            .toList()
    events shouldHaveSize expectAmount
    events.forAll {
        it.get(OUTBOX.EVENT_TYPE) shouldBe "REAP_VARIANT"
    }

    return events.map {
        Json.decodeFromString<ReapVariantEvent>(it.getNonNull(OUTBOX.PAYLOAD).data())
    }
}
