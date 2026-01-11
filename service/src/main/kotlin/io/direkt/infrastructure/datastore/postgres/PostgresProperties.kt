package io.direkt.infrastructure.datastore.postgres

import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.DATASTORE
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.DatabasePropertyKeys.POSTGRES
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.DatabasePropertyKeys.PostgresPropertyKeys.DATABASE
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.DatabasePropertyKeys.PostgresPropertyKeys.HOST
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.DatabasePropertyKeys.PostgresPropertyKeys.PASSWORD
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.DatabasePropertyKeys.PostgresPropertyKeys.PORT
import io.direkt.infrastructure.properties.ConfigurationPropertyKeys.DatabasePropertyKeys.PostgresPropertyKeys.USER
import io.direkt.infrastructure.tryGetConfig
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString

data class PostgresProperties(
    val database: String,
    val user: String,
    val host: String,
    val port: Int,
    val password: String?,
)

fun Application.createPostgresProperties(): PostgresProperties {
    val postgresProperties =
        environment.config
            .tryGetConfig(DATASTORE)
            ?.tryGetConfig(POSTGRES)
    val password =
        postgresProperties
            ?.tryGetString(PASSWORD)
    val host =
        postgresProperties
            ?.tryGetString(HOST)
            ?: "localhost"
    val database =
        postgresProperties
            ?.tryGetString(DATABASE)
            ?: "direkt"
    val port =
        postgresProperties
            ?.tryGetString(PORT)
            ?.toInt()
            ?: 5432
    val user =
        postgresProperties
            ?.tryGetString(USER)
            ?: throw IllegalStateException("$DATASTORE.$POSTGRES.$USER must be supplied if using Postgres datastore")

    return PostgresProperties(
        database = database,
        user = user,
        host = host,
        port = port,
        password = password,
    )
}
