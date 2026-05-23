package io.konifer.domain.ports

import io.konifer.domain.event.DomainEvent

interface EventPublisher {
    suspend fun publish(event: DomainEvent)
}
