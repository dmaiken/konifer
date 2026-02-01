package io.konifer.infrastructure.objectstore.inmemory

import io.konifer.domain.ports.ObjectStore
import io.konifer.infrastructure.objectstore.ObjectStoreTest

class InMemoryObjectStoreTest : ObjectStoreTest() {
    override fun createObjectStore(): ObjectStore = InMemoryObjectStore()
}
