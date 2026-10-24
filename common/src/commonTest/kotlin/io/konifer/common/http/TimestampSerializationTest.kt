package io.konifer.common.http

import io.konifer.common.asset.AssetClass
import io.konifer.common.asset.AssetSource
import io.konifer.common.image.Rotate
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant

class TimestampSerializationTest :
    StringSpec({
        val instant = Instant.parse("2026-10-24T14:30:00.123456789Z")
        val asset =
            AssetResponse(
                `class` = AssetClass.IMAGE,
                alt = null,
                entryId = 42,
                labels = emptyMap(),
                tags = emptySet(),
                source = AssetSource.UPLOAD,
                externalSourceAddress = null,
                sourceUrl = null,
                variants = emptyList(),
                createdAt = instant,
                modifiedAt = instant,
            )
        val link =
            AssetLinkResponse(
                url = "https://example.com/image",
                expiresAt = instant,
                alt = null,
                lqip = LQIPResponse(null, null),
                attributes =
                    AttributesResponse(
                        height = 100,
                        width = 200,
                        format = "jpeg",
                        colorSpace = "srgb",
                        pageCount = 1,
                        loop = null,
                        pixels = null,
                        rotate = Rotate.ZERO,
                        horizontalFlip = false,
                    ),
            )

        "public timestamps serialize as UTC instants without losing fractional precision" {
            val encoded = Json.encodeToString(asset)
            val fields = Json.parseToJsonElement(encoded).jsonObject
            fields.getValue("createdAt").jsonPrimitive.content shouldBe instant.toString()
            fields.getValue("modifiedAt").jsonPrimitive.content shouldBe instant.toString()
            Json.decodeFromString<AssetResponse>(encoded) shouldBe asset
            val encodedLink = Json.encodeToString(link)
            Json
                .parseToJsonElement(encodedLink)
                .jsonObject
                .getValue("expiresAt")
                .jsonPrimitive.content shouldBe instant.toString()
            Json.decodeFromString<AssetLinkResponse>(encodedLink) shouldBe link
        }

        "explicit numeric offsets deserialize to the same instant and reserialize with Z" {
            val encoded = Json.encodeToString(asset).replace(instant.toString(), "2026-10-24T08:30:00.123456789-06:00")
            val decoded = Json.decodeFromString<AssetResponse>(encoded)
            decoded shouldBe asset
            Json.encodeToString(decoded) shouldBe Json.encodeToString(asset)
            Json.decodeFromString<AssetLinkResponse>(
                Json.encodeToString(link).replace(instant.toString(), "2026-10-24T14:30:00.123456789+00:00"),
            ) shouldBe link
        }

        "offset-less timestamps are rejected in all public timestamp fields" {
            for (field in listOf("createdAt", "modifiedAt")) {
                val encoded =
                    Json.encodeToString(asset).replace(
                        "\"$field\":\"$instant\"",
                        "\"$field\":\"2026-10-24T14:30:00.123456789\"",
                    )
                shouldThrow<IllegalArgumentException> { Json.decodeFromString<AssetResponse>(encoded) }
            }
            shouldThrow<IllegalArgumentException> {
                Json.decodeFromString<AssetLinkResponse>(
                    Json.encodeToString(link).replace(instant.toString(), "2026-10-24T14:30:00.123456789"),
                )
            }
        }

        "expiry remains nullable" {
            val encoded = Json.encodeToString(link.copy(expiresAt = null))
            Json.parseToJsonElement(encoded).jsonObject.getValue("expiresAt") shouldBe JsonNull
            Json.decodeFromString<AssetLinkResponse>(encoded).expiresAt shouldBe null
        }
    })
