package io.direkt.infrastructure.http.serialization

import kotlinx.serialization.json.Json

/**
 * Serializer that is configured to not serialize null values
 */
val format: Json = Json { explicitNulls = false }
