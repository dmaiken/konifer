package io.konifer.asset.fetch

import io.konifer.domain.event.DomainEvent
import io.konifer.domain.event.VariantAccessedEvent
import io.konifer.domain.ports.EventPublisher
import java.util.concurrent.CopyOnWriteArrayList

internal class ObservedEventPublisher(
    private val delegate: EventPublisher,
) : EventPublisher {
    private val recordedAccesses = CopyOnWriteArrayList<VariantAccessedEvent>()
    val accesses: List<VariantAccessedEvent> get() = recordedAccesses.toList()

    fun reset() {
        recordedAccesses.clear()
    }

    override suspend fun publish(event: DomainEvent) {
        if (event is VariantAccessedEvent) {
            recordedAccesses.add(event)
        }
        delegate.publish(event)
    }
}
