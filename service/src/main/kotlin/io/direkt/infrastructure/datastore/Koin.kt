package io.direkt.infrastructure.datastore

import io.direkt.domain.ports.AssetRepository
import io.direkt.infrastructure.datastore.inmemory.InMemoryAssetRepository
import io.direkt.infrastructure.datastore.postgres.PostgresAssetRepository
import io.direkt.infrastructure.datastore.postgres.configureJOOQ
import io.direkt.infrastructure.datastore.postgres.connectToPostgres
import io.direkt.infrastructure.datastore.postgres.createPostgresProperties
import io.direkt.infrastructure.datastore.postgres.migrateSchema
import io.direkt.infrastructure.datastore.postgres.scheduling.configureScheduling
import io.direkt.infrastructure.properties.ConfigurationProperties.DATASTORE
import io.direkt.infrastructure.properties.ConfigurationProperties.DatabaseConfigurationProperties.PROVIDER
import io.direkt.infrastructure.tryGetConfig
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString
import io.r2dbc.spi.ConnectionFactory
import org.jooq.DSLContext
import org.koin.core.module.Module
import org.koin.dsl.module

fun Application.assetRepositoryModule(): Module =
    module {
        val datastoreProvider =
            environment.config
                .tryGetConfig(DATASTORE)
                ?.tryGetString(PROVIDER)
                ?.let {
                    DataStoreProvider.fromConfig(it)
                } ?: DataStoreProvider.default
        when (datastoreProvider) {
            DataStoreProvider.IN_MEMORY -> {
                single<AssetRepository> {
                    InMemoryAssetRepository()
                }
            }
            DataStoreProvider.POSTGRES -> {
                val properties = createPostgresProperties()
                val connectionFactory = connectToPostgres(properties)
                migrateSchema(connectionFactory)
                configureScheduling(properties)
                single<ConnectionFactory> {
                    connectionFactory
                }
                single<DSLContext>(createdAtStart = true) {
                    configureJOOQ(get())
                }
                single<AssetRepository> {
                    PostgresAssetRepository(get())
                }
            }
        }
    }
