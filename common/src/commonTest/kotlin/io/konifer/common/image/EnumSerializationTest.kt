package io.konifer.common.image

import io.konifer.common.image.pixel.PixelDataType
import io.konifer.common.image.pixel.PixelLayout
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class EnumSerializationTest :
    FunSpec({
        serializationTests(
            mapOf(
                Filter.NONE to "",
                Filter.BLACK_WHITE to "black_white",
                Filter.GRAYSCALE to "grayscale",
                Filter.SEPIA to "sepia",
            ),
        )
        serializationTests(
            mapOf(
                Flip.H to "h",
                Flip.V to "v",
                Flip.NONE to "",
            ),
        )
        serializationTests(
            mapOf(
                Fit.FIT to "fit",
                Fit.FILL to "fill",
                Fit.STRETCH to "stretch",
                Fit.CROP to "crop",
            ),
        )
        serializationTests(
            mapOf(
                Gravity.CENTER to "center",
                Gravity.ENTROPY to "entropy",
                Gravity.ATTENTION to "attention",
            ),
        )
        serializationTests(
            mapOf(
                ImageFormat.JPEG to "jpg",
                ImageFormat.PNG to "png",
                ImageFormat.WEBP to "webp",
                ImageFormat.AVIF to "avif",
                ImageFormat.JPEG_XL to "jxl",
                ImageFormat.HEIC to "heic",
                ImageFormat.GIF to "gif",
                ImageFormat.PIXELS to "pixels",
            ),
        )
        serializationTests(
            mapOf(
                MetadataType.EXIF to "exif",
                MetadataType.XMP to "xmp",
                MetadataType.IPTC to "iptc",
                MetadataType.ICC to "icc",
            ),
        )
        serializationTests(
            mapOf(
                Rotate.ZERO to "0",
                Rotate.NINETY to "90",
                Rotate.ONE_HUNDRED_EIGHTY to "180",
                Rotate.TWO_HUNDRED_SEVENTY to "270",
                Rotate.AUTO to "auto",
            ),
        )
        serializationTests(
            mapOf(
                TransformableColorSpace.ORIGIN to "origin",
                TransformableColorSpace.SRGB to "srgb",
                TransformableColorSpace.P3 to "p3",
                TransformableColorSpace.GRAYSCALE to "grayscale",
            ),
        )
        serializationTests(
            mapOf(
                PixelDataType.UINT8 to "uint8",
                PixelDataType.INT8 to "int8",
                PixelDataType.UINT16 to "uint16",
                PixelDataType.INT16 to "int16",
                PixelDataType.UINT32 to "uint32",
                PixelDataType.INT32 to "int32",
                PixelDataType.FLOAT32 to "float32",
                PixelDataType.FLOAT64 to "float64",
                PixelDataType.COMPLEX64 to "complex64",
                PixelDataType.COMPLEX128 to "complex128",
            ),
        )
        serializationTests(
            mapOf(
                PixelLayout.PLANAR to "planar",
                PixelLayout.INTERLEAVED to "interleaved",
            ),
        )

        context("SortedMetadataTypeSerializer") {
            test("serializes metadata in sorted order using its serial names") {
                val metadata = listOf(MetadataType.XMP, MetadataType.IPTC, MetadataType.EXIF, MetadataType.ICC)

                Json.encodeToString(SortedMetadataTypeSerializer, metadata) shouldBe
                    """["exif","icc","iptc","xmp"]"""
            }

            test("deserializes serial names while preserving input order") {
                Json.decodeFromString(SortedMetadataTypeSerializer, """["xmp","exif","icc","iptc"]""") shouldBe
                    listOf(MetadataType.XMP, MetadataType.EXIF, MetadataType.ICC, MetadataType.IPTC)
            }

            test("supports an empty metadata list") {
                Json.encodeToString(SortedMetadataTypeSerializer, emptyList()) shouldBe "[]"
                Json.decodeFromString(SortedMetadataTypeSerializer, "[]") shouldBe emptyList()
            }
        }
    })

private inline fun <reified T : Enum<T>> FunSpec.serializationTests(cases: Map<T, String>) {
    context(checkNotNull(T::class.simpleName)) {
        test("covers every enum entry") {
            cases.keys shouldBe enumValues<T>().toSet()
        }

        cases.forEach { (value, serialName) ->
            test("${value.name} serializes as a JSON string with its serial name") {
                Json.encodeToString(value) shouldBe "\"$serialName\""
            }

            test("the serial name for ${value.name} deserializes to the enum entry") {
                Json.decodeFromString<T>("\"$serialName\"") shouldBe value
            }
        }

        test("rejects an unknown serial name") {
            shouldThrow<SerializationException> {
                Json.decodeFromString<T>("\"unknown\"")
            }
        }
    }
}
