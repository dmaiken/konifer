package io.direkt.infrastructure.`object`

import io.direkt.domain.ports.ObjectRepository
import io.direkt.infrastructure.inmemory.InMemoryObjectRepository

class InMemoryObjectRepositoryTest : ObjectRepositoryTest() {
    override fun createObjectStore(): ObjectRepository = InMemoryObjectRepository()
}
