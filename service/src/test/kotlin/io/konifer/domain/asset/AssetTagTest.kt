package io.konifer.domain.asset

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AssetTagTest {
    @Test
    fun `can create asset tag`() {
        AssetTag("hero").value shouldBe "hero"
    }

    @Test
    fun `can create asset tag at max length`() {
        val value = "t".repeat(256)

        AssetTag(value).value shouldBe value
    }

    @Test
    fun `cannot create blank asset tag`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                AssetTag(" ")
            }

        exception.message shouldBe "Asset tag cannot be blank"
    }

    @Test
    fun `cannot create asset tag above max length`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                AssetTag("t".repeat(257))
            }

        exception.message shouldBe "Tags exceeds max length of 256 characters"
    }

    @Test
    fun `empty asset tags has empty set and is empty`() {
        AssetTags.EMPTY.asSet().shouldBeEmpty()
        AssetTags.EMPTY.isNotEmpty() shouldBe false
    }

    @Test
    fun `can create asset tags from set`() {
        val tags =
            AssetTags.from(
                setOf(
                    "hero",
                    "landing",
                ),
            )

        tags.asSet() shouldContainExactly setOf("hero", "landing")
        tags.isNotEmpty() shouldBe true
    }

    @Test
    fun `can create asset tags at max count`() {
        val tags = (1..MAX_TAGS).map { "tag-$it" }.toSet()

        AssetTags.from(tags).asSet() shouldContainExactly tags
    }

    @Test
    fun `cannot create asset tags above max count`() {
        val tags = (1..MAX_TAGS + 1).map { "tag-$it" }.toSet()

        val exception =
            shouldThrow<IllegalArgumentException> {
                AssetTags.from(tags)
            }

        exception.message shouldBe "Cannot have more than 50 tags"
    }

    @Test
    fun `can convert set to asset tags`() {
        val tags = setOf("hero")

        tags.toAssetTags().asSet() shouldContainExactly tags
    }
}
