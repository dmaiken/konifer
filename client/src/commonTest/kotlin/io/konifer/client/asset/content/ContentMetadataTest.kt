package io.konifer.client.asset.content

import io.konifer.client.KoniferClient
import io.konifer.client.KoniferResult
import io.konifer.client.assets.fetch.AssetContent
import io.konifer.client.assets.fetch.AssetContentMetadata
import io.konifer.client.assets.fetch.ContentDelivery
import io.konifer.client.harness.httpClient
import io.konifer.common.http.AttributesResponse
import io.konifer.common.http.LQIPResponse
import io.konifer.common.http.PixelAttributesResponse
import io.konifer.common.image.Rotate
import io.konifer.common.image.pixel.PixelDataType
import io.konifer.common.image.pixel.PixelLayout
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.async
import kotlinx.io.IOException

class ContentMetadataTest :
    FunSpec({
        val bytes = byteArrayOf(1, 2, 3)
        val attributes =
            AttributesResponse(
                width = 20,
                height = 10,
                format = "pixels",
                colorSpace = "SRGB",
                rotate = Rotate.NINETY,
                horizontalFlip = true,
                pageCount = 1,
                loop = 0,
                pixels = PixelAttributesResponse("bgr", PixelLayout.PLANAR, PixelDataType.UINT8),
            )
        val lqip = LQIPResponse("blurhash", "thumbhash")
        val values =
            mapOf(
                "k-attr-width" to "20",
                "K-Attr-Height" to "10",
                "K-Attr-Format" to "pixels",
                "K-Attr-Rotate" to "90",
                "K-Attr-Horizontal-Flip" to "true",
                "K-Attr-Color-Space" to "SRGB",
                "K-Attr-Page-Count" to "1",
                "K-Attr-Loop" to "0",
                "K-Attr-Pixels-Channels" to "bgr",
                "K-Attr-Pixels-Layout" to "planar",
                "K-Attr-Pixels-Data-Type" to "uint8",
                "K-Alt" to "an image",
                "K-LQIP-Blurhash" to "blurhash",
                "K-LQIP-Thumbhash" to "thumbhash",
            )

        test("buffered content decodes attributes and existing metadata from case insensitive headers") {
            val client =
                KoniferClient(
                    httpClient {
                        MockEngine {
                            respond(
                                bytes,
                                headers = headersOf(*values.map { it.key to listOf(it.value) }.toTypedArray()),
                            )
                        }
                    },
                )
            try {
                client.assets("pixels").originalVariant().fetchContent() shouldBe
                    KoniferResult.Success(AssetContent(bytes, attributes, "an image", lqip))
            } finally {
                client.close()
            }
        }

        test("streamed content returns the same metadata after closing the destination") {
            val client =
                KoniferClient(
                    httpClient {
                        MockEngine {
                            respond(
                                bytes,
                                headers = headersOf(*values.map { it.key to listOf(it.value) }.toTypedArray()),
                            )
                        }
                    },
                )
            val destination = ByteChannel()
            val received = async { destination.toByteArray() }
            try {
                client.assets("pixels").originalVariant().fetchAndWriteContentTo(destination) shouldBe
                    KoniferResult.Success(AssetContentMetadata(attributes, "an image", lqip))
                received.await() shouldBe bytes
                destination.isClosedForWrite shouldBe true
            } finally {
                client.close()
            }
        }

        test("inapplicable and absent optional headers produce nullable fields") {
            val headers =
                values.filterKeys {
                    !it.startsWith("K-Attr-Pixels-") &&
                        it !in setOf("K-Attr-Loop", "K-Alt", "K-LQIP-Blurhash", "K-LQIP-Thumbhash")
                } +
                    ("K-Attr-Format" to "png")
            val client =
                KoniferClient(
                    httpClient {
                        MockEngine {
                            respond(
                                bytes,
                                headers =
                                    headersOf(
                                        *headers
                                            .map {
                                                it.key to
                                                    listOf(it.value)
                                            }.toTypedArray(),
                                    ),
                            )
                        }
                    },
                )
            try {
                client.assets("image").originalVariant().fetchContent() shouldBe
                    KoniferResult.Success(AssetContent(bytes, attributes.copy(format = "png", pixels = null, loop = null)))
            } finally {
                client.close()
            }
        }

        test("redirect delivery leaves metadata unavailable even when the delivery host supplies matching headers") {
            val client =
                KoniferClient(
                    httpClient {
                        MockEngine { request ->
                            if (request.url.host == "delivery.example") {
                                respond(bytes, headers = headersOf(*values.map { it.key to listOf(it.value) }.toTypedArray()))
                            } else {
                                respond(
                                    "",
                                    HttpStatusCode.TemporaryRedirect,
                                    headersOf(HttpHeaders.Location, "https://delivery.example/image"),
                                )
                            }
                        }
                    },
                )
            try {
                client.assets("image").originalVariant().fetchContent(ContentDelivery.FOLLOW_REDIRECT) shouldBe
                    KoniferResult.Success(AssetContent(bytes))
            } finally {
                client.close()
            }
        }

        listOf(
            "k-attr-width" to "invalid",
            "K-Attr-Rotate" to "auto",
            "K-Attr-Horizontal-Flip" to "invalid",
            "K-Attr-Pixels-Layout" to "unknown",
            "K-Attr-Pixels-Data-Type" to "unknown",
            "K-Attr-Loop" to "invalid",
            "K-Attr-Height" to null,
            "K-Attr-Pixels-Channels" to null,
        ).forEach { (name, value) ->
            test("invalid or partial metadata for $name fails buffered and streaming reads") {
                val headers = if (value == null) values - name else values + (name to value)
                val client =
                    KoniferClient(
                        httpClient {
                            MockEngine {
                                respond(
                                    bytes,
                                    headers =
                                        headersOf(
                                            *headers
                                                .map {
                                                    it.key to
                                                        listOf(it.value)
                                                }.toTypedArray(),
                                        ),
                                )
                            }
                        },
                    )
                val destination = ByteChannel()
                try {
                    client
                        .assets("image")
                        .originalVariant()
                        .fetchContent()
                        .shouldBeInstanceOf<KoniferResult.Failure.InvalidResponse>()
                    val failure =
                        client
                            .assets(
                                "image",
                            ).originalVariant()
                            .fetchAndWriteContentTo(destination)
                            .shouldBeInstanceOf<KoniferResult.Failure.InvalidResponse>()
                    destination.isClosedForWrite shouldBe true
                    destination.closedCause.shouldBeInstanceOf<IOException>().message shouldBe failure.cause.message
                } finally {
                    client.close()
                }
            }
        }
    })
