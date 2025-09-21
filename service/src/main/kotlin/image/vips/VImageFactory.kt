package io.image.vips

import app.photofox.vipsffm.VCustomSource
import app.photofox.vipsffm.VCustomSource.ReadCallback
import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VImage.newFromSource
import app.photofox.vipsffm.VSource
import app.photofox.vipsffm.VipsError
import app.photofox.vipsffm.VipsOption
import io.asset.AssetStreamContainer
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import kotlin.math.min

object VImageFactory {
    /**
     * Adapted from [VImage.newFromStream].
     *
     * This blocks!! Don't call it from [kotlinx.coroutines.Dispatchers.Default]
     */
    @JvmStatic
    fun newFromContainer(
        arena: Arena,
        container: AssetStreamContainer,
        vararg options: VipsOption,
    ): VImage {
        val source = newSourceFromContainer(arena, container)

        return newFromSource(arena, source, *options)
    }

    /**
     * Adapted from [VSource.newFromInputStream]
     */
    @JvmStatic
    private fun newSourceFromContainer(
        arena: Arena,
        container: AssetStreamContainer,
    ): VSource {
        val readCallback =
            ReadCallback { dataPointer: MemorySegment, length: Long ->
                if (length < 0) {
                    throw VipsError("invalid length to read provided: $length")
                }
                // bytebuffer only supports reading int max bytes at a time
                val clippedLength = min(length, Int.MAX_VALUE.toLong()).toInt()
                val bytes =
                    try {
                        runBlocking {
                            container.readNBytes(clippedLength, false)
                        }
                    } catch (e: IOException) {
                        throw VipsError("failed to read bytes from stream", e)
                    }

                val buffer = dataPointer.asSlice(0, clippedLength.toLong()).asByteBuffer()
                buffer.put(bytes)
                bytes.size.toLong()
            }
        return VCustomSource(arena, readCallback)
    }
}
