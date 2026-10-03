package io.konifer.client.asset.link

import io.konifer.common.http.AssetLinkResponse
import io.konifer.common.http.AttributeResponse
import io.konifer.common.http.LQIPResponse
import io.konifer.common.image.ImageFormat

fun createLinkResponse() =
    AssetLinkResponse(
        url = "https://localhost:9999",
        expiresAt = null,
        alt = "an image",
        lqip =
            LQIPResponse(
                blurhash = "blurhash",
                thumbhash = "thumbhash",
            ),
        attributes =
            AttributeResponse(
                height = 100,
                width = 200,
                format = ImageFormat.PNG.format,
                colorSpace = "srgb",
                loop = null,
                pageCount = 1,
            ),
    )
