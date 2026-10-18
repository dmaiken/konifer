package io.konifer.asset.store

import io.konifer.BaseFunctionalTest
import io.konifer.common.image.ImageFormat
import io.konifer.matchers.shouldHaveHttpError
import io.konifer.testInMemory
import org.junit.jupiter.api.Test

class StoreAssetPixelsTest : BaseFunctionalTest() {
    @Test
    fun `cannot store raw pixel input`() =
        testInMemory {
            val sourcePixels =
                listOf(10, 20, 30, 64, 128, 200, 255, 127, 40, 50, 60, 192, 70, 80, 90, 255)
                    .map(Int::toByte)
                    .toByteArray()
            konifer()
                .assets("/known-pixels")
                .newAsset()
                .fromBytes(sourcePixels, ImageFormat.PIXELS)
                .store() shouldHaveHttpError 403
        }
}
