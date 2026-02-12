package io.konifer.infrastructure.datastore.postgres

import io.konifer.infrastructure.property.ConfigurationPropertyKeys.DATASTORE
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.DataStorePropertyKeys.POSTGRES
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.DataStorePropertyKeys.PostgresPropertyKeys.DATABASE
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.DataStorePropertyKeys.PostgresPropertyKeys.HOST
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.DataStorePropertyKeys.PostgresPropertyKeys.PASSWORD
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.DataStorePropertyKeys.PostgresPropertyKeys.PORT
import io.konifer.infrastructure.property.ConfigurationPropertyKeys.DataStorePropertyKeys.PostgresPropertyKeys.USER
import io.konifer.infrastructure.tryGetConfig
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString

data class PostgresProperties(
    val database: String,
    val user: String,
    val host: String,
    val port: Int,
    val password: String,
)

fun Application.createPostgresProperties(): PostgresProperties {
    val postgresProperties =
        environment.config
            .tryGetConfig(DATASTORE)
            ?.tryGetConfig(POSTGRES)
    val password =
        postgresProperties
            ?.tryGetString(PASSWORD)
            ?: ""
    val host =
        postgresProperties
            ?.tryGetString(HOST)
            ?: "localhost"
    val database =
        postgresProperties
            ?.tryGetString(DATABASE)
            ?: "konifer"
    val port =
        postgresProperties
            ?.tryGetString(PORT)
            ?.toInt()
            ?: 5432
    val user =
        postgresProperties
            ?.tryGetString(USER)
            ?: "postgres"

    return PostgresProperties(
        database = database,
        user = user,
        host = host,
        port = port,
        password = password,
    )
}
