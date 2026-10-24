package io.konifer.application.usecase.fetch

import io.ktor.http.Url
import kotlin.time.Instant

data class DeliveryUrl(
    val url: Url,
    val expiresAt: Instant?,
)
