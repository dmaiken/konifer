package io.konifer.application.usecase.fetch

import io.konifer.domain.asset.AssetDeliveryInformation
import io.ktor.utils.io.ByteWriteChannel

data class AssetContent(
    val delivery: AssetDeliveryInformation,
    val writeTo: suspend (ByteWriteChannel) -> Unit,
)
