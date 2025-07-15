package io.asset.variant

import asset.variant.ImageVariantAttributes
import asset.variant.VariantParameterGenerator
import image.model.ImageAttributes
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import net.openhft.hashing.LongHashFunction
import org.junit.jupiter.api.Test

class VariantParameterGeneratorTest {
    private val xx3 = LongHashFunction.xx3()
    private val variantParameterGenerator = VariantParameterGenerator()

    @Test
    fun `can generate variant attributes and key`() {
        val expectedAttributes =
            Json.encodeToString(
                ImageVariantAttributes(
                    height = 100,
                    width = 100,
                    mimeType = "image/jpeg",
                ),
            )
        val (attributes, key) =
            variantParameterGenerator.generateImageVariantAttributes(
                imageAttributes =
                    ImageAttributes(
                        height = 100,
                        width = 100,
                        mimeType = "image/jpeg",
                    ),
            )

        key shouldBe xx3.hashBytes(expectedAttributes.toByteArray(Charsets.UTF_8))
        attributes shouldBe expectedAttributes
    }

    @Test
    fun `the same attributes and key are generated based on the same parameters`() {
        val expectedAttributes =
            Json.encodeToString(
                ImageVariantAttributes(
                    height = 100,
                    width = 100,
                    mimeType = "image/jpeg",
                ),
            )
        val expectedKey = xx3.hashBytes(expectedAttributes.toByteArray(Charsets.UTF_8))
        val (attributes1, key1) =
            variantParameterGenerator.generateImageVariantAttributes(
                imageAttributes =
                    ImageAttributes(
                        height = 100,
                        width = 100,
                        mimeType = "image/jpeg",
                    ),
            )
        val (attributes2, key2) =
            variantParameterGenerator.generateImageVariantAttributes(
                imageAttributes =
                    ImageAttributes(
                        height = 100,
                        width = 100,
                        mimeType = "image/jpeg",
                    ),
            )

        attributes1 shouldBe attributes2 shouldBe expectedAttributes
        key1 shouldBe key2 shouldBe expectedKey
    }
}
