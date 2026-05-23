package io.konifer.domain.ports

import io.konifer.domain.event.DomainEvent
import kotlinx.coroutines.flow.SharedFlow

interface EventBus {
    val events: SharedFlow<DomainEvent>
}
