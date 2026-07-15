package io.konifer.domain.asset

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AssetAltTest {
    @Test
    fun `can create asset alt`() {
        AssetAlt("An image alt").value shouldBe "An image alt"
    }

    @Test
    fun `can create asset alt at max length`() {
        val value = "a".repeat(125)

        AssetAlt(value).value shouldBe value
    }

    @Test
    fun `cannot create asset alt above max length`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                AssetAlt("a".repeat(126))
            }

        exception.message shouldBe "Asset alt cannot exceed 125 characters"
    }

    @Test
    fun `can convert string to asset alt`() {
        "An image alt".toAssetAlt() shouldBe AssetAlt("An image alt")
    }
}
