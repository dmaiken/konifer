package io.direkt.infrastructure.datastore

import io.direkt.domain.ports.AssetRepository
import io.direkt.infrastructure.datastore.inmemory.InMemoryAssetRepository
import io.direkt.infrastructure.datastore.postgres.PostgresAssetRepository
import io.direkt.infrastructure.datastore.postgres.connectToPostgres
import io.direkt.infrastructure.datastore.postgres.createPostgresProperties
import io.direkt.infrastructure.datastore.postgres.scheduling.configureScheduling
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.DATASTORE
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.DatabasePropertyKeys.PROVIDER
import io.direkt.infrastructure.tryGetConfig
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString
import io.r2dbc.spi.ConnectionFactory
import name.nkonev.r2dbc.migrate.core.R2dbcMigrate
import name.nkonev.r2dbc.migrate.core.R2dbcMigrateProperties
import name.nkonev.r2dbc.migrate.reader.ReflectionsClasspathResourceReader
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import org.jooq.tools.LoggerListener
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
                val dslContext = configureR2dbcJOOQ(connectionFactory)
                configureScheduling(properties, dslContext)
                single<ConnectionFactory> {
                    connectionFactory
                }
                single<DSLContext>(createdAtStart = true) {
                    dslContext
                }
                single<AssetRepository> {
                    PostgresAssetRepository(get())
                }
            }
        }
    }

fun configureR2dbcJOOQ(connectionFactory: ConnectionFactory): DSLContext {
    val config =
        DefaultConfiguration().apply {
            setSQLDialect(SQLDialect.POSTGRES)
            setConnectionFactory(connectionFactory)
            setExecuteListener(LoggerListener())
            settings()
                .withExecuteLogging(true)
                .withRenderFormatted(true)
        }

    return DSL.using(config)
}

fun migrateSchema(connectionFactory: ConnectionFactory) {
    val migrateProperties =
        R2dbcMigrateProperties().apply {
            setResourcesPath("db/migration")
        }

    R2dbcMigrate.migrate(connectionFactory, migrateProperties, ReflectionsClasspathResourceReader(), null, null).block()
}
