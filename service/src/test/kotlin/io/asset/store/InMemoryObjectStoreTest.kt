package io.asset.store

class InMemoryObjectStoreTest : ObjectStoreTest() {
    override fun createObjectStore(): ObjectStore {
        return InMemoryObjectStore()
    }
}
