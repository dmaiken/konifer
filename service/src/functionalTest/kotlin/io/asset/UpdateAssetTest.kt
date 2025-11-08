package io.asset

import io.config.testInMemory
import org.junit.jupiter.api.Test

class UpdateAssetTest {
    @Test
    fun `can update asset metadata`() =
        testInMemory {
        }

    @Test
    fun `updating asset that does not exists returns not found`() =
        testInMemory {
        }
}
