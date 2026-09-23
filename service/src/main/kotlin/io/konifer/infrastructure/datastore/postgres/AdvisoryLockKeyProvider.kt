package io.konifer.infrastructure.datastore.postgres

import io.konifer.domain.asset.AssetId
import java.nio.ByteBuffer
import java.security.MessageDigest

object AdvisoryLockKeyProvider {
    fun assetLockKey(assetId: AssetId): Long {
        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest(
                    "konifer:asset:v1:${assetId.value}"
                        .toByteArray(Charsets.UTF_8),
                )

        return ByteBuffer.wrap(digest).long
    }
}
