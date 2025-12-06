package io.direkt.infrastructure.inmemory

import io.direkt.domain.ports.ObjectRepository
import org.koin.core.module.Module
import org.koin.dsl.module

fun inMemoryObjectStoreModule(): Module =
    module {
        single<ObjectRepository> {
            InMemoryObjectRepository()
        }
    }
