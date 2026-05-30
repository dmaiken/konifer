package io.konifer.infrastructure.event

import io.konifer.domain.event.DomainEvent
import io.konifer.domain.ports.EventBus
import io.konifer.domain.ports.EventPublisher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class InMemoryEventBus :
    EventPublisher,
    EventBus {
    private val _events =
        MutableSharedFlow<DomainEvent>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.SUSPEND,
        )

    override val events = _events.asSharedFlow()

    override suspend fun publish(event: DomainEvent) {
        _events.emit(event)
    }
}
