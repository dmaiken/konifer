package io.inmemory

import io.direkt.asset.store.InMemoryObjectStore
import io.direkt.asset.store.ObjectStore
import org.koin.core.module.Module
import org.koin.dsl.module

fun inMemoryObjectStoreModule(): Module =
    module {
        single<ObjectStore> {
            InMemoryObjectStore()
        }
    }
