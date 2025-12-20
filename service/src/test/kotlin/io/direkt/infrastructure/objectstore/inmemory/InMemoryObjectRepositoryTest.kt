package io.direkt.infrastructure.objectstore.inmemory

import io.direkt.domain.ports.ObjectRepository
import io.direkt.infrastructure.objectstore.ObjectRepositoryTest

class InMemoryObjectRepositoryTest : ObjectRepositoryTest() {
    override fun createObjectStore(): ObjectRepository = InMemoryObjectRepository()
}
