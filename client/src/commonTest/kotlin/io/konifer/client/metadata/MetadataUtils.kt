package io.konifer.client.metadata

import io.konifer.common.asset.AssetClass
import io.konifer.common.asset.AssetSource
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.AttributeResponse
import io.konifer.common.http.ErrorResponse
import io.konifer.common.http.LQIPResponse
import io.konifer.common.http.PaddingResponse
import io.konifer.common.http.TransformationResponse
import io.konifer.common.http.VariantResponse
import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Flip
import io.konifer.common.image.Gravity
import io.konifer.common.image.Rotate
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlin.time.Clock

fun createMetadataResponse() =
    AssetResponse(
        `class` = AssetClass.IMAGE,
        alt = "an image",
        entryId = 0,
        labels =
            mapOf(
                "phone" to "iphone",
                "having-fun" to "true",
            ),
        tags = setOf("phone", "fun"),
        source = AssetSource.UPLOAD,
        sourceUrl = null,
        variants =
            listOf(
                VariantResponse(
                    isOriginalVariant = true,
                    storeBucket = "assets",
                    storeKey = "key",
                    attributes =
                        AttributeResponse(
                            height = 200,
                            width = 100,
                            format = "jpeg",
                            pageCount = 1,
                            loop = 2,
                        ),
                    transformation =
                        TransformationResponse(
                            width = 100,
                            height = 200,
                            fit = Fit.FIT,
                            gravity = Gravity.ATTENTION,
                            format = "jpeg",
                            rotate = Rotate.ONE_HUNDRED_EIGHTY,
                            flip = Flip.H,
                            filter = Filter.GREYSCALE,
                            blur = 100,
                            quality = 90,
                            padding =
                                PaddingResponse(
                                    amount = 20,
                                    color = listOf(10, 10, 10),
                                ),
                        ),
                    lqip =
                        LQIPResponse(
                            blurhash = "blurhash",
                            thumbhash = "thumbhash",
                        ),
                ),
            ),
        createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        modifiedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    )

fun configureMockEngineHappy(
    expectedPath: String,
    response: AssetResponse,
    statusCode: HttpStatusCode = HttpStatusCode.OK,
): MockEngine =
    MockEngine { request ->
        request.url.encodedPath shouldBe expectedPath

        respond(
            content = Json.encodeToString(response),
            status = statusCode,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

fun configureMockEngineHappy(
    expectedPath: String,
    response: List<AssetResponse>,
    statusCode: HttpStatusCode = HttpStatusCode.OK,
): MockEngine =
    MockEngine { request ->
        request.url.encodedPath shouldBe expectedPath
        request.url.parameters["limit"] shouldBe response.size.toString()

        respond(
            content = Json.encodeToString(response),
            status = statusCode,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
