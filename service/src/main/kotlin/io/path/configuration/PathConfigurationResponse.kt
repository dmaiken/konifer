package io.path.configuration

import io.serializers.LocalDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class PathConfigurationResponse(
    val pathMatcher: String,
    val allowedContentTypes: List<String>?,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class)
    val modifiedAt: LocalDateTime,
)
