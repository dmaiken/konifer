package io.asset.repository

import io.asset.handler.AssetSource
import io.asset.handler.dto.StoreAssetDto
import io.asset.model.StoreAssetRequest
import io.asset.store.PersistResult
import io.image.model.Attributes
import io.image.model.ImageFormat
import io.image.model.LQIPs
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
