package io.konifer.clientV2.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStream

internal fun inputStreamChunks(open: () -> InputStream): () -> Flow<ByteArray> =
    {
        flow {
            open().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) emit(buffer.copyOf(count))
                }
            }
        }.flowOn(Dispatchers.IO)
    }
