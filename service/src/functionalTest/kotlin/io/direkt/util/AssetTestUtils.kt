package io.direkt.util

import io.direkt.BaseTestcontainerTest.Companion.BOUNDARY
import io.direkt.infrastructure.StoreAssetRequest
import io.direkt.infrastructure.http.APP_CACHE_STATUS
import io.direkt.infrastructure.http.AssetLinkResponse
import io.direkt.infrastructure.http.AssetResponse
import io.direkt.service.context.modifiers.OrderBy
import io.direkt.service.context.modifiers.ReturnFormat
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.fullPath
import io.ktor.http.path
import io.ktor.utils.io.readRemaining
import kotlinx.io.asInputStream
import kotlinx.serialization.json.Json

suspend inline fun <reified T> storeAssetMultipartSource(
    client: HttpClient,
    asset: ByteArray,
    request: T,
    path: String = "profile",
    expectedStatus: HttpStatusCode = HttpStatusCode.Created,
    verifyLocationHeader: Boolean = true,
): Pair<Headers, AssetResponse?> =
    client
        .post("/assets/$path") {
            contentType(ContentType.MultiPart.FormData)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "metadata",
                            Json.encodeToString<T>(request),
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/json")
                            },
                        )
                        append(
                            "file",
                            asset,
                            Headers.build {
                                append(HttpHeaders.ContentType, "image/png")
                                append(HttpHeaders.ContentDisposition, "filename=\"ktor_logo.png\"")
                            },
                        )
                    },
                    BOUNDARY,
                    ContentType.MultiPart.FormData.withParameter("boundary", BOUNDARY),
                ),
            )
        }.let { response ->
            response.status shouldBe expectedStatus
            val body =
                if (response.status == HttpStatusCode.Created) {
                    // validate location header
                    response.headers[HttpHeaders.Location] shouldNotBe null
                    if (verifyLocationHeader) {
                        val locationUrl = Url(response.headers[HttpHeaders.Location]!!)
                        client.get(locationUrl.fullPath).apply {
                            status shouldBe HttpStatusCode.OK
                        }
                    }
                    response.body<AssetResponse>().apply {
                        entryId shouldBeGreaterThan -1
                        variants shouldHaveSize 1 // original variant
                        modifiedAt shouldBeAfter createdAt
                    }
                } else {
                    null
                }

            Pair(response.headers, body)
        }

suspend fun storeAssetUrlSource(
    client: HttpClient,
    request: StoreAssetRequest,
    path: String = "profile",
    expectedStatus: HttpStatusCode = HttpStatusCode.Created,
): AssetResponse? =
    client
        .post("/assets/$path") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.let { response ->
            response.status shouldBe expectedStatus
            if (response.status == HttpStatusCode.Created) {
                val responseBody = response.body<AssetResponse>()
                // validate location header
                response.headers[HttpHeaders.Location] shouldNotBe null
                val locationUrl = Url(response.headers[HttpHeaders.Location]!!)
                client.get(locationUrl.fullPath).apply {
                    status shouldBe HttpStatusCode.OK
                }
                responseBody.apply {
                    entryId shouldBeGreaterThan -1
                    variants shouldHaveSize 1 // original variant
                    modifiedAt shouldBeAfter createdAt
                }
            } else {
                null
            }
        }

suspend fun fetchAssetViaRedirect(
    client: HttpClient,
    path: String = "profile",
    entryId: Long? = null,
    profile: String? = null,
    height: Int? = null,
    width: Int? = null,
    mimeType: String? = null,
    fit: String? = null,
    gravity: String? = null,
    rotate: String? = null,
    flip: String? = null,
    filter: String? = null,
    blur: Int? = null,
    quality: Int? = null,
    pad: Int? = null,
    background: String? = null,
    expectCacheHit: Boolean? = null,
    expectedStatusCode: HttpStatusCode = HttpStatusCode.TemporaryRedirect,
): ByteArray? {
    val urlBuilder = URLBuilder()
    if (entryId != null) {
        urlBuilder.path("/assets/$path/-/redirect/entry/$entryId")
    } else {
        urlBuilder.path("/assets/$path/-/redirect")
    }

    attachVariantModifiers(urlBuilder, profile, height, width, mimeType, fit, gravity, rotate, flip, filter, blur, quality, pad, background)
    val url = urlBuilder.build()
    val fetchResponse =
        client.get(url.fullPath).apply {
            status shouldBe expectedStatusCode
            if (expectedStatusCode == HttpStatusCode.TemporaryRedirect) {
                headers[HttpHeaders.Location] shouldContain "http://"

                if (expectCacheHit == true) {
                    headers[APP_CACHE_STATUS] shouldBeEqualIgnoringCase "hit"
                }
                if (expectCacheHit == false) {
                    headers[APP_CACHE_STATUS] shouldBeEqualIgnoringCase "miss"
                }
            } else {
                headers.contains(HttpHeaders.Location) shouldBe false
                headers.contains(APP_CACHE_STATUS) shouldBe false
            }
        }
    if (fetchResponse.status != HttpStatusCode.TemporaryRedirect) {
        return null
    }
    val location = Url(fetchResponse.headers[HttpHeaders.Location]!!).fullPath
    val objectStoreResponse = client.get(location)
    val channel = objectStoreResponse.bodyAsChannel()
    objectStoreResponse.status shouldBe HttpStatusCode.OK

    return channel.readRemaining().asInputStream().use {
        it.readAllBytes()
    }
}

suspend fun fetchAssetContent(
    client: HttpClient,
    path: String = "profile",
    entryId: Long? = null,
    profile: String? = null,
    height: Int? = null,
    width: Int? = null,
    mimeType: String? = null,
    fit: String? = null,
    gravity: String? = null,
    rotate: String? = null,
    flip: String? = null,
    filter: String? = null,
    blur: Int? = null,
    quality: Int? = null,
    pad: Int? = null,
    background: String? = null,
    expectCacheHit: Boolean? = null,
    expectedMimeType: String? = null,
    expectedStatusCode: HttpStatusCode = HttpStatusCode.OK,
): Pair<HttpResponse, ByteArray?> {
    val urlBuilder = URLBuilder()
    if (entryId != null) {
        urlBuilder.path("/assets/$path/-/content/entry/$entryId")
    } else {
        urlBuilder.path("/assets/$path/-/content")
    }

    attachVariantModifiers(urlBuilder, profile, height, width, mimeType, fit, gravity, rotate, flip, filter, blur, quality, pad, background)
    val url = urlBuilder.build()
    client.get(url.fullPath).apply {
        status shouldBe expectedStatusCode
        return if (status == HttpStatusCode.OK) {
            headers.contains(HttpHeaders.Location) shouldBe false
            if (expectedMimeType != null) {
                headers[HttpHeaders.ContentType] shouldBe expectedMimeType
            } else if (mimeType != null) {
                (
                    headers[HttpHeaders.ContentType] shouldBe mimeType
                )
            } else {
                headers[HttpHeaders.ContentType] shouldNotBe null
            }

            if (expectCacheHit == true) {
                headers[APP_CACHE_STATUS] shouldBeEqualIgnoringCase "hit"
            }
            if (expectCacheHit == false) {
                headers[APP_CACHE_STATUS] shouldBeEqualIgnoringCase "miss"
            }

            headers[HttpHeaders.ContentDisposition] shouldBe null
            headers[HttpHeaders.ETag] shouldNotBe null
            Pair(this, bodyAsBytes())
        } else {
            headers[HttpHeaders.Location] shouldBe null
            headers[HttpHeaders.ContentType] shouldBe null
            headers[HttpHeaders.ContentDisposition] shouldBe null
            headers[HttpHeaders.ETag] shouldBe null

            Pair(this, null)
        }
    }
}

suspend fun fetchAssetContentDownload(
    client: HttpClient,
    path: String = "profile",
    entryId: Long? = null,
    profile: String? = null,
    height: Int? = null,
    width: Int? = null,
    mimeType: String? = null,
    fit: String? = null,
    gravity: String? = null,
    rotate: String? = null,
    flip: String? = null,
    filter: String? = null,
    blur: Int? = null,
    quality: Int? = null,
    pad: Int? = null,
    background: String? = null,
    expectCacheHit: Boolean? = null,
    expectedMimeType: String? = null,
    expectedStatusCode: HttpStatusCode = HttpStatusCode.OK,
): Pair<HttpResponse, ByteArray?> {
    val urlBuilder = URLBuilder()
    if (entryId != null) {
        urlBuilder.path("/assets/$path/-/download/entry/$entryId")
    } else {
        urlBuilder.path("/assets/$path/-/download")
    }

    attachVariantModifiers(urlBuilder, profile, height, width, mimeType, fit, gravity, rotate, flip, filter, blur, quality, pad, background)
    val url = urlBuilder.build()
    client.get(url.fullPath).apply {
        status shouldBe expectedStatusCode
        return if (status == HttpStatusCode.OK) {
            headers.contains(HttpHeaders.Location) shouldBe false
            if (expectedMimeType != null) {
                headers[HttpHeaders.ContentType] shouldBe expectedMimeType
            } else if (mimeType != null) {
                (
                    headers[HttpHeaders.ContentType] shouldBe mimeType
                )
            } else {
                headers[HttpHeaders.ContentType] shouldNotBe null
            }

            if (expectCacheHit == true) {
                headers[APP_CACHE_STATUS] shouldBeEqualIgnoringCase "hit"
            }
            if (expectCacheHit == false) {
                headers[APP_CACHE_STATUS] shouldBeEqualIgnoringCase "miss"
            }
            headers[HttpHeaders.ContentDisposition] shouldNotBe null
            headers[HttpHeaders.ETag] shouldNotBe null

            Pair(this, bodyAsBytes())
        } else {
            headers.contains(HttpHeaders.Location) shouldBe false
            headers.contains(HttpHeaders.ContentType) shouldBe false
            headers.contains(HttpHeaders.ContentDisposition) shouldBe false

            Pair(this, null)
        }
    }
}

suspend fun fetchAssetLink(
    client: HttpClient,
    path: String = "profile",
    entryId: Long? = null,
    profile: String? = null,
    height: Int? = null,
    width: Int? = null,
    mimeType: String? = null,
    fit: String? = null,
    gravity: String? = null,
    rotate: String? = null,
    flip: String? = null,
    filter: String? = null,
    blur: Int? = null,
    quality: Int? = null,
    pad: Int? = null,
    background: String? = null,
    expectCacheHit: Boolean? = null,
    signature: String? = null,
    expectedStatusCode: HttpStatusCode = HttpStatusCode.OK,
): AssetLinkResponse? {
    val urlBuilder = URLBuilder()
    if (entryId != null) {
        urlBuilder.path("/assets/$path/-/link/entry/$entryId")
    } else {
        urlBuilder.path("/assets/$path/-/link")
    }

    attachVariantModifiers(urlBuilder, profile, height, width, mimeType, fit, gravity, rotate, flip, filter, blur, quality, pad, background)
    signature?.let {
        urlBuilder.parameters["s"] = signature
    }
    val fetchUrl = urlBuilder.build()
    client.get(fetchUrl.fullPath).apply {
        status shouldBe expectedStatusCode
        headers[HttpHeaders.Location] shouldBe null
        headers[HttpHeaders.ETag] shouldBe null
        return if (expectedStatusCode == HttpStatusCode.OK) {
            contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")

            if (expectCacheHit == true) {
                headers[APP_CACHE_STATUS] shouldBeEqualIgnoringCase "hit"
            }
            if (expectCacheHit == false) {
                headers[APP_CACHE_STATUS] shouldBeEqualIgnoringCase "miss"
            }

            body<AssetLinkResponse>().apply {
                url shouldContain "http://"
            }
        } else {
            null
        }
    }
}

suspend fun assertAssetDoesNotExist(
    client: HttpClient,
    path: String = "profile",
    entryId: Long? = null,
) {
    ReturnFormat.entries.forEach { format ->
        val urlBuilder = URLBuilder()
        if (entryId != null) {
            urlBuilder.path("/assets/$path/-/${format.name}/entry/$entryId")
        } else {
            urlBuilder.path("/assets/$path/-/${format.name}")
        }
        client.get(urlBuilder.build()).apply {
            status shouldBe HttpStatusCode.NotFound
            headers.contains(HttpHeaders.Location) shouldBe false
        }
    }
}

suspend fun fetchAssetMetadata(
    client: HttpClient,
    path: String,
    entryId: Long? = null,
    orderBy: OrderBy? = null, // CREATED by default
    labels: Map<String, String> = emptyMap(),
    expectedStatus: HttpStatusCode = HttpStatusCode.OK,
): AssetResponse? =
    if (entryId != null) {
        "/assets/$path/-/metadata/entry/$entryId"
    } else {
        "/assets/$path/-/metadata".let {
            if (orderBy != null) {
                "$it/${orderBy.name.lowercase()}"
            } else {
                it
            }
        }
    }.let { requestPath ->
        val urlBuilder = URLBuilder()
        urlBuilder.path(requestPath)
        labels.forEach { label ->
            urlBuilder.parameters.append(label.key, label.value)
        }
        val response = client.get(urlBuilder.build())
        response.status shouldBe expectedStatus
        response.headers[HttpHeaders.ETag] shouldBe null

        if (response.status == HttpStatusCode.NotFound) {
            null
        } else {
            response.body<AssetResponse>().apply {
                entryId shouldBe entryId
            }
        }
    }

suspend fun fetchAllAssetMetadata(
    client: HttpClient,
    path: String,
    orderBy: OrderBy = OrderBy.CREATED,
    limit: Int = 1,
    all: Boolean = false,
    expectedStatus: HttpStatusCode = HttpStatusCode.OK,
): List<AssetResponse> {
    val limit =
        if (all) {
            "all"
        } else {
            limit.toString()
        }
    val requestPath = "/assets/$path/-/metadata/${orderBy.name.lowercase()}/$limit"
    val response = client.get(requestPath)
    response.status shouldBe expectedStatus

    return if (response.status == HttpStatusCode.NotFound) {
        emptyList()
    } else {
        response.body<List<AssetResponse>>()
    }
}

suspend fun deleteAsset(
    client: HttpClient,
    path: String = "profile",
    entryId: Long? = null,
    expectedStatusCode: HttpStatusCode = HttpStatusCode.NoContent,
) {
    if (entryId != null) {
        client.delete("/assets/$path/-/entry/$entryId").status shouldBe expectedStatusCode
    } else {
        client.delete("/assets/$path").status shouldBe expectedStatusCode
    }
}

suspend fun deleteAssetsAtPath(
    client: HttpClient,
    path: String = "profile",
    labels: Map<String, String> = emptyMap(),
    orderBy: OrderBy = OrderBy.CREATED,
    limit: Int = 1,
    all: Boolean = false,
    expectedStatusCode: HttpStatusCode = HttpStatusCode.NoContent,
) {
    val limit =
        if (all) {
            "all"
        } else {
            limit.toString()
        }
    val urlBuilder = URLBuilder()
    urlBuilder.path("/assets/$path/-/$orderBy/$limit")
    labels.forEach { label ->
        urlBuilder.parameters.append(label.key, label.value)
    }
    client.delete(urlBuilder.build()).status shouldBe expectedStatusCode
}

suspend fun deleteAssetsRecursivelyAtPath(
    client: HttpClient,
    path: String = "profile",
    labels: Map<String, String> = emptyMap(),
    expectedStatusCode: HttpStatusCode = HttpStatusCode.NoContent,
) {
    val urlBuilder = URLBuilder()
    urlBuilder.path("/assets/$path/-/recursive")
    labels.forEach { label ->
        urlBuilder.parameters.append(label.key, label.value)
    }
    client.delete(urlBuilder.build()).status shouldBe expectedStatusCode
}

suspend fun updateAsset(
    client: HttpClient,
    location: String,
    body: StoreAssetRequest,
    expectedStatusCode: HttpStatusCode = HttpStatusCode.OK,
): Pair<Headers, AssetResponse?> {
    val response =
        client.put(Url(location).fullPath) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    response.status shouldBe expectedStatusCode
    response.headers[HttpHeaders.Location] shouldBe null
    val body =
        if (response.status == HttpStatusCode.OK) {
            response.body<AssetResponse>()
        } else {
            null
        }
    return Pair(response.headers, body)
}

private fun attachVariantModifiers(
    urlBuilder: URLBuilder,
    profile: String? = null,
    height: Int? = null,
    width: Int? = null,
    mimeType: String? = null,
    fit: String? = null,
    gravity: String? = null,
    rotate: String? = null,
    flip: String? = null,
    filter: String? = null,
    blur: Int? = null,
    quality: Int? = null,
    pad: Int? = null,
    background: String? = null,
) {
    if (profile != null) {
        urlBuilder.parameters.append("profile", profile)
    }
    if (height != null) {
        urlBuilder.parameters.append("h", height.toString())
    }
    if (width != null) {
        urlBuilder.parameters.append("w", width.toString())
    }
    if (mimeType != null) {
        urlBuilder.parameters.append("mimeType", mimeType)
    }
    if (fit != null) {
        urlBuilder.parameters.append("fit", fit)
    }
    if (gravity != null) {
        urlBuilder.parameters.append("g", gravity)
    }
    if (rotate != null) {
        urlBuilder.parameters.append("r", rotate)
    }
    if (flip != null) {
        urlBuilder.parameters.append("f", flip)
    }
    if (filter != null) {
        urlBuilder.parameters.append("filter", filter)
    }
    if (blur != null) {
        urlBuilder.parameters.append("blur", blur.toString())
    }
    if (quality != null) {
        urlBuilder.parameters.append("q", quality.toString())
    }
    if (pad != null) {
        urlBuilder.parameters.append("pad", pad.toString())
    }
    if (background != null) {
        urlBuilder.parameters.append("bg", background)
    }
}
