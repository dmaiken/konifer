package io.direkt.asset.repository

import io.direkt.asset.handler.AssetSource
import io.direkt.asset.handler.dto.StoreAssetDto
import io.direkt.asset.model.StoreAssetRequest
import io.direkt.asset.store.PersistResult
import io.direkt.image.model.Attributes
import io.direkt.image.model.ImageFormat
import io.direkt.image.model.LQIPs
import java.util.UUID

fun createAssetDto(
    treePath: String,
    labels: Map<String, String> =
        mapOf(
            "phone" to "iphone",
            "customer" to "vip",
        ),
    source: AssetSource = AssetSource.UPLOAD,
    url: String? = null,
): StoreAssetDto =
    StoreAssetDto(
        path = treePath,
        request =
            StoreAssetRequest(
                alt = "an image",
                labels = labels,
                tags =
                    setOf(
                        "scary",
                        "spooky",
                    ),
                url = url,
            ),
        attributes =
            Attributes(
                width = 100,
                height = 100,
                format = ImageFormat.PNG,
            ),
        persistResult =
            PersistResult(
                key = UUID.randomUUID().toString(),
                bucket = "bucket",
            ),
        lqips = LQIPs.Factory.NONE,
        source = source,
    )
