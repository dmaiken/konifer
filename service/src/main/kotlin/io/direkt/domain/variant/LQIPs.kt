package io.direkt.domain.variant

import io.direkt.infrastructure.http.LQIPResponse
import kotlinx.serialization.Serializable

@Serializable
data class LQIPs(
    val blurhash: String?,
    val thumbhash: String?,
) {
    companion object Factory {
        val NONE = LQIPs(null, null)
    }

    fun toResponse(): LQIPResponse =
        LQIPResponse(
            blurhash = blurhash,
            thumbhash = thumbhash,
        )
}
