package io.konifer.infrastructure.objectstore.inmemory

import io.konifer.domain.ports.ObjectRepository
import io.konifer.infrastructure.objectstore.ObjectRepositoryTest

class InMemoryObjectRepositoryTest : ObjectRepositoryTest() {
    override fun createObjectStore(): ObjectRepository = InMemoryObjectRepository()
}
