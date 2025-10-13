package io.inmemory

import io.asset.store.InMemoryObjectStore
import io.asset.store.ObjectStore
import org.koin.core.module.Module
import org.koin.dsl.module

fun inMemoryObjectStoreModule(): Module =
    module {
        single<ObjectStore> {
            InMemoryObjectStore()
        }
    }
