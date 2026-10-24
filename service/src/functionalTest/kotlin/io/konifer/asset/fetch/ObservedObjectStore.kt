package io.konifer.asset.fetch

import io.konifer.domain.ports.FetchResult
import io.konifer.domain.ports.ObjectStore
import io.ktor.utils.io.ByteWriteChannel
import java.util.concurrent.CopyOnWriteArrayList

internal class ObservedObjectStore(
    private val delegate: ObjectStore,
) : ObjectStore by delegate {
    private val recordedFetches = CopyOnWriteArrayList<Pair<String, String>>()
    val fetches: List<Pair<String, String>> get() = recordedFetches.toList()

    fun reset() {
        recordedFetches.clear()
    }

    override suspend fun fetch(
        bucket: String,
        key: String,
        channel: ByteWriteChannel,
    ): FetchResult {
        recordedFetches.add(bucket to key)
        return delegate.fetch(bucket, key, channel)
    }
}
