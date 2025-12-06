package io.direkt.infrastructure.database

import io.r2dbc.spi.ConnectionFactory
import org.jooq.DSLContext
import org.koin.core.module.Module
import org.koin.dsl.module

fun dbModule(connectionFactory: ConnectionFactory?): Module =
    module {
        connectionFactory?.let {
            single<ConnectionFactory> {
                connectionFactory
            }
            single<DSLContext> {
                configureJOOQ(get())
            }
        }
    }
