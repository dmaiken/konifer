package io.konifer.infrastructure.vips.transformer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.MetadataType
import io.konifer.domain.image.ColorSpace
import io.konifer.domain.variant.MetadataTransformation
import io.konifer.domain.variant.Transformation
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class StripMetadataTest {
    @Nested
    inner class TransformTests {
        @Test
        fun `removes exif tags if exif is to be stripped`() {
            val image =
                javaClass.getResourceAsStream("/images/metadata/exif-xmp-iptc.jpg")!!.use {
                    it.readBytes()
                }
            Vips.run { arena ->
                val source = VImage.newFromBytes(arena, image)
                val exifPredicate: (String) -> Boolean = {
                    it.startsWith("exif") || it == "jpeg-thumbnail-data" || it == "exif-data"
                }
                val exifTags = source.fields.filter(exifPredicate)
                val nonExifTags = source.fields.filterNot(exifPredicate)

                val result =
                    StripMetadata.transform(
                        arena = arena,
                        source = source,
                        transformation =
                            Transformation(
                                width = source.width,
                                height = source.height,
                                format = ImageFormat.JPEG,
                                metadata =
                                    MetadataTransformation(
                                        strip = setOf(MetadataType.EXIF),
                                    ),
                                colorSpace = ColorSpace.SRGB,
                            ),
                    )

                result.requiresLqipRegeneration shouldBe false
                result.processed.fields shouldNotContainAnyOf exifTags
                result.processed.fields shouldContainAll nonExifTags
            }
        }

        @Test
        fun `removes xmp tags if xmp is to be stripped`() {
            val image =
                javaClass.getResourceAsStream("/images/metadata/exif-xmp-iptc.jpg")!!.use {
                    it.readBytes()
                }
            Vips.run { arena ->
                val source = VImage.newFromBytes(arena, image)
                val xmpTags = source.fields.filter { it == "xmp-data" }
                val nonXmpTags = source.fields.filterNot { it == "xmp-data" }

                val result =
                    StripMetadata.transform(
                        arena = arena,
                        source = source,
                        transformation =
                            Transformation(
                                width = source.width,
                                height = source.height,
                                format = ImageFormat.JPEG,
                                metadata =
                                    MetadataTransformation(
                                        strip = setOf(MetadataType.XMP),
                                    ),
                                colorSpace = ColorSpace.SRGB,
                            ),
                    )

                result.requiresLqipRegeneration shouldBe false
                result.processed.fields shouldNotContainAnyOf xmpTags
                result.processed.fields shouldContainAll nonXmpTags
            }
        }

        @Test
        fun `removes iptc tags if iptc is to be stripped`() {
            val image =
                javaClass.getResourceAsStream("/images/metadata/exif-xmp-iptc.jpg")!!.use {
                    it.readBytes()
                }
            Vips.run { arena ->
                val source = VImage.newFromBytes(arena, image)
                val iptcTags = source.fields.filter { it == "iptc-data" }
                val nonIptcTags = source.fields.filterNot { it == "iptc-data" }

                val result =
                    StripMetadata.transform(
                        arena = arena,
                        source = source,
                        transformation =
                            Transformation(
                                width = source.width,
                                height = source.height,
                                format = ImageFormat.JPEG,
                                metadata =
                                    MetadataTransformation(
                                        strip = setOf(MetadataType.IPTC),
                                    ),
                                colorSpace = ColorSpace.SRGB,
                            ),
                    )

                result.requiresLqipRegeneration shouldBe false
                result.processed.fields shouldNotContainAnyOf iptcTags
                result.processed.fields shouldContainAll nonIptcTags
            }
        }

        @Test
        fun `does not throw if nothing to remove`() {
            val image =
                javaClass.getResourceAsStream("/images/metadata/exif-xmp-iptc.jpg")!!.use {
                    it.readBytes()
                }
            Vips.run { arena ->
                val source = VImage.newFromBytes(arena, image)
                // Remove all metadata
                source.fields
                    .filter { it.startsWith("exif-") }
                    .forEach { source.remove(it) }
                source.remove("jpeg-thumbnail-data")
                source.remove("xmp-data")
                source.remove("iptc-data")

                val result =
                    shouldNotThrowAny {
                        StripMetadata.transform(
                            arena = arena,
                            source = source,
                            Transformation(
                                width = source.width,
                                height = source.height,
                                format = ImageFormat.JPEG,
                                metadata =
                                    MetadataTransformation(
                                        strip = setOf(MetadataType.XMP, MetadataType.IPTC, MetadataType.EXIF),
                                    ),
                                colorSpace = ColorSpace.SRGB,
                            ),
                        )
                    }

                result.requiresLqipRegeneration shouldBe false
            }
        }

        @Test
        fun `does nothing if nothing is to be removed`() {
            val image =
                javaClass.getResourceAsStream("/images/metadata/exif-xmp-iptc.jpg")!!.use {
                    it.readBytes()
                }
            Vips.run { arena ->
                val source = VImage.newFromBytes(arena, image)
                val beforeFields = ArrayList(source.fields)

                val result =
                    StripMetadata.transform(
                        arena = arena,
                        source = source,
                        Transformation(
                            width = source.width,
                            height = source.height,
                            format = ImageFormat.JPEG,
                            metadata =
                                MetadataTransformation(
                                    strip = emptySet(),
                                ),
                            colorSpace = ColorSpace.SRGB,
                        ),
                    )

                result.requiresLqipRegeneration shouldBe false
                result.processed.fields shouldContainExactly beforeFields
            }
        }
    }
}
