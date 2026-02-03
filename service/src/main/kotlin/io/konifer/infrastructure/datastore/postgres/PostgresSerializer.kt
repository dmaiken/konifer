package io.konifer.infrastructure.datastore.postgres

import kotlinx.serialization.json.Json

/**
 * Serializer that is configured to not serialize null values.
 *
 * These settings are very important! We do not want to serialize null fields or default values.
 */
val postgresJson: Json =
    Json {
        encodeDefaults = false
        explicitNulls = false
    }
