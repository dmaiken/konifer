package io.direkt.serialization

import kotlinx.serialization.json.Json

/**
 * Serializer that is configured to not serialize null values
 */
val format: Json = Json { explicitNulls = false }
