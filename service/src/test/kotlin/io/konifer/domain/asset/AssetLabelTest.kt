package io.konifer.domain.asset

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AssetLabelTest {
    @Test
    fun `can create label key`() {
        LabelKey("color").key shouldBe "color"
    }

    @Test
    fun `can create label key at max length`() {
        val key = "k".repeat(128)

        LabelKey(key).key shouldBe key
    }

    @Test
    fun `cannot create blank label key`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                LabelKey(" ")
            }

        exception.message shouldBe "Label key cannot be blank"
    }

    @Test
    fun `cannot create label key above max length`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                LabelKey("k".repeat(129))
            }

        exception.message shouldBe "Label key cannot exceed 128 characters"
    }

    @Test
    fun `can create label value`() {
        LabelValue("blue").value shouldBe "blue"
    }

    @Test
    fun `can create label value at max length`() {
        val value = "v".repeat(256)

        LabelValue(value).value shouldBe value
    }

    @Test
    fun `cannot create blank label value`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                LabelValue("")
            }

        exception.message shouldBe "Label value cannot be blank"
    }

    @Test
    fun `cannot create label value above max length`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                LabelValue("v".repeat(257))
            }

        exception.message shouldBe "Label value cannot exceed 256 characters"
    }

    @Test
    fun `default asset labels has empty map and is empty`() {
        AssetLabels.empty.asMap().shouldBeEmpty()
        AssetLabels.empty.isNotEmpty() shouldBe false
    }

    @Test
    fun `can create asset labels from map`() {
        val labels =
            AssetLabels.from(
                mapOf(
                    "color" to "blue",
                    "source" to "camera",
                ),
            )

        labels.asMap() shouldContainExactly
            mapOf(
                "color" to "blue",
                "source" to "camera",
            )
        labels.isNotEmpty() shouldBe true
    }

    @Test
    fun `can create asset labels at max count`() {
        val labels = (1..50).associate { "key-$it" to "value-$it" }

        AssetLabels.from(labels).asMap() shouldContainExactly labels
    }

    @Test
    fun `cannot create asset labels above max count`() {
        val labels = (1..51).associate { "key-$it" to "value-$it" }

        val exception =
            shouldThrow<IllegalArgumentException> {
                AssetLabels.from(labels)
            }

        exception.message shouldBe "Cannot have more than 50 labels"
    }

    @Test
    fun `can convert map to asset labels`() {
        val labels = mapOf("color" to "blue")

        labels.toAssetLabels().asMap() shouldContainExactly labels
    }

    @Test
    fun `can merge asset label into another`() {
        val labels = AssetLabels.from(mapOf("color" to "blue"))
        val otherLabels = AssetLabels.from(mapOf("source" to "camera"))

        labels.merge(otherLabels).asMap() shouldContainExactly
            mapOf(
                "color" to "blue",
                "source" to "camera",
            )
    }

    @Test
    fun `can merge asset label into another with merging labels taking priority`() {
        val labels = AssetLabels.from(mapOf("color" to "blue"))
        val otherLabels = AssetLabels.from(mapOf("color" to "red"))

        labels.merge(otherLabels).asMap() shouldContainExactly mapOf("color" to "red")
    }
}
