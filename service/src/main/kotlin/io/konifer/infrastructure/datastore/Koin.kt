package io.konifer.infrastructure.datastore

import io.konifer.domain.ports.AssetRepository
import io.konifer.infrastructure.datastore.inmemory.InMemoryAssetRepository
import io.konifer.infrastructure.datastore.postgres.PostgresAssetRepository
import io.konifer.infrastructure.datastore.postgres.PostgresVariantRepository
import io.konifer.infrastructure.datastore.postgres.createPostgresProperties
import io.konifer.infrastructure.datastore.postgres.metrics.PostgresFlushVariantMetricsTimer
import io.konifer.infrastructure.datastore.postgres.metrics.PostgresVariantMetricsWriter
import io.konifer.infrastructure.datastore.postgres.postgres
import io.konifer.infrastructure.datastore.postgres.scheduling.ScheduledJobProperties
import io.konifer.infrastructure.datastore.postgres.scheduling.configureScheduledJobs
import io.konifer.infrastructure.path.extractRawHocon
import io.ktor.server.application.Application
import io.r2dbc.spi.ConnectionFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig
import name.nkonev.r2dbc.migrate.core.R2dbcMigrate
import name.nkonev.r2dbc.migrate.core.R2dbcMigrateProperties
import name.nkonev.r2dbc.migrate.reader.ReflectionsClasspathResourceReader
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import org.jooq.tools.LoggerListener
import org.koin.core.module.Module
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

@OptIn(ExperimentalSerializationApi::class)
fun Application.assetRepositoryModule(datastoreProvider: DataStoreProvider): Module =
    module {
        when (datastoreProvider) {
            DataStoreProvider.IN_MEMORY -> {
                single<InMemoryAssetRepository>() bind AssetRepository::class
            }
            DataStoreProvider.POSTGRES -> {
                val properties = createPostgresProperties()
                val connectionFactory = postgres(properties)
                migrateSchema(connectionFactory)
                val dslContext = configureR2dbcJOOQ(connectionFactory)

                val rawConfig = environment.config.extractRawHocon()
                val scheduledJobProperties =
                    if (rawConfig.hasPath("postgres.scheduled-jobs")) {
                        Hocon.decodeFromConfig<ScheduledJobProperties>(
                            rawConfig.getConfig("postgres.scheduled-jobs"),
                        )
                    } else {
                        ScheduledJobProperties()
                    }
                configureScheduledJobs(properties, dslContext, scheduledJobProperties)
                single<PostgresAssetRepository>() bind AssetRepository::class
                single<ConnectionFactory> { connectionFactory }
                single<DSLContext> { dslContext } withOptions {
                    createdAtStart()
                }
                single<PostgresVariantRepository>()
                single<PostgresVariantMetricsWriter>() withOptions {
                    createdAtStart()
                }
                single<PostgresFlushVariantMetricsTimer> {
                    PostgresFlushVariantMetricsTimer(
                        interval = scheduledJobProperties.variantMetricsFlushInterval,
                        scope = get(),
                        drainSignal = get(),
                    )
                } withOptions {
                    createdAtStart()
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
