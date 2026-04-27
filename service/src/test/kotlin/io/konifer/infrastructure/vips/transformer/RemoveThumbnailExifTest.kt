package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.domain.variant.Transformation
import io.konifer.infrastructure.vips.pipeline.AppliedTransformation
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class RemoveThumbnailExifTest {
    @Nested
    inner class TransformTests {
        @Test
        fun `removes thumbnail tags`() {
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                    it.readBytes()
                }
            Vips.run { arena ->
                val source = VImage.newFromBytes(arena, image)
                val thumbnailPredicate: (String) -> Boolean = {
                    it.startsWith("exif-ifd1") || it == "jpeg-thumbnail-data"
                }
                val thumbnailTags = source.fields.filter(thumbnailPredicate)
                val nonThumbnailTags = source.fields.filterNot(thumbnailPredicate)

                val result =
                    RemoveThumbnailExif.transform(
                        arena = arena,
                        source = source,
                        // Transformation doesn't matter here
                        transformation = Transformation.ORIGINAL_VARIANT,
                    )

                result.requiresLqipRegeneration shouldBe false
                result.processed.fields shouldNotContainAnyOf thumbnailTags
                result.processed.fields shouldContainAll nonThumbnailTags
                result.processed.fields shouldNotContain "jpeg-thumbnail-data"
            }
        }

        @Test
        fun `does not throw if nothing to remove`() {
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                    it.readBytes()
                }
            Vips.run { arena ->
                val source = VImage.newFromBytes(arena, image)
                // Remove all metadata
                source.fields
                    .filter { it.startsWith("exif") }
                    .forEach { source.remove(it) }
                source.remove("jpeg-thumbnail-data")

                val result =
                    shouldNotThrowAny {
                        RemoveThumbnailExif.transform(
                            arena = arena,
                            source = source,
                            // Transformation doesn't matter here
                            transformation = Transformation.ORIGINAL_VARIANT,
                        )
                    }

                result.requiresLqipRegeneration shouldBe false
            }
        }
    }

    @Nested
    inner class RequiresTransformationTests {
        @Test
        fun `requires transformation if transformations have been applied`() {
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                    it.readBytes()
                }
            Vips.run { arena ->
                val source = VImage.newFromBytes(arena, image)

                RemoveThumbnailExif.requiresTransformation(
                    arena = arena,
                    source = source,
                    transformation = Transformation.ORIGINAL_VARIANT,
                    appliedTransformations =
                        listOf(
                            AppliedTransformation(
                                name = "something",
                                exceptionMessage = null,
                            ),
                        ),
                ) shouldBe true
            }
        }

        @Test
        fun `does not require transformation if transformations have not been applied`() {
            val image =
                javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.use {
                    it.readBytes()
                }
            Vips.run { arena ->
                val source = VImage.newFromBytes(arena, image)

                RemoveThumbnailExif.requiresTransformation(
                    arena = arena,
                    source = source,
                    transformation = Transformation.ORIGINAL_VARIANT,
                    appliedTransformations = emptyList(),
                ) shouldBe false
            }
        }
    }
}
