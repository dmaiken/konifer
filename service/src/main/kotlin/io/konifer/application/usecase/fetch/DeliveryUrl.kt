package io.konifer.application.usecase.fetch

import io.ktor.http.Url
import kotlinx.datetime.LocalDateTime

data class DeliveryUrl(
    val url: Url,
    val expiresAt: LocalDateTime?,
)
