package io.konifer.infrastructure.datastore.postgres.scheduling

import io.konifer.infrastructure.datastore.postgres.getNonNull
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import konifer.jooq.tables.references.OUTBOX
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.serialization.json.Json
import org.jooq.DSLContext
import reactor.core.publisher.Flux

suspend fun fetchVariantDeletedEvents(
    dslContext: DSLContext,
    expectAmount: Int,
): List<VariantDeletedEvent> {
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
        it.get(OUTBOX.EVENT_TYPE) shouldBe "VARIANT_DELETED"
    }

    return events.map {
        Json.decodeFromString<VariantDeletedEvent>(it.getNonNull(OUTBOX.PAYLOAD).data())
    }
}
