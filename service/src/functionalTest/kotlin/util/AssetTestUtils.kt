package util

import BaseTestcontainerTest.Companion.BOUNDARY
import asset.model.AssetLinkResponse
import asset.model.AssetResponse
import asset.model.StoreAssetRequest
import io.APP_CACHE_STATUS
import io.asset.context.ReturnFormat
import io.kotest.matchers.collections.shouldHaveSize
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
import io.ktor.client.request.setBody
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

suspend fun storeAsset(
    client: HttpClient,
    asset: ByteArray,
    request: StoreAssetRequest,
    path: String = "profile",
    expectedStatus: HttpStatusCode = HttpStatusCode.Created,
): AssetResponse? {
    return client.post("/assets/$path") {
        contentType(ContentType.MultiPart.FormData)
        setBody(
            MultiPartFormDataContent(
                formData {
                    append(
                        "metadata",
                        Json.encodeToString<StoreAssetRequest>(request),
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
        if (response.status == HttpStatusCode.Created) {
            response.body<AssetResponse>().apply {
                entryId shouldNotBe null
                createdAt shouldNotBe null
                variants shouldHaveSize 1 // original variant
            }
        } else {
            null
        }
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
    expectCacheHit: Boolean? = null,
    expectedStatusCode: HttpStatusCode = HttpStatusCode.TemporaryRedirect,
): ByteArray? {
    val urlBuilder = URLBuilder()
    if (entryId != null) {
        urlBuilder.path("/assets/$path/-/redirect/entry/$entryId")
    } else {
        urlBuilder.path("/assets/$path/-/redirect")
    }

    attachVariantModifiers(urlBuilder, profile, height, width, mimeType, fit, gravity, rotate, flip, filter, blur, quality)
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
    expectCacheHit: Boolean? = null,
    expectedMimeType: String? = null,
    expectedStatusCode: HttpStatusCode = HttpStatusCode.OK,
): ByteArray? {
    val urlBuilder = URLBuilder()
    if (entryId != null) {
        urlBuilder.path("/assets/$path/-/content/entry/$entryId")
    } else {
        urlBuilder.path("/assets/$path/-/content")
    }

    attachVariantModifiers(urlBuilder, profile, height, width, mimeType, fit, gravity, rotate, flip, filter, blur, quality)
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

            bodyAsBytes()
        } else {
            headers.contains(HttpHeaders.Location) shouldBe false
            headers.contains(HttpHeaders.ContentType) shouldBe false

            null
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
    expectCacheHit: Boolean? = null,
    expectedStatusCode: HttpStatusCode = HttpStatusCode.OK,
): AssetLinkResponse? {
    val urlBuilder = URLBuilder()
    if (entryId != null) {
        urlBuilder.path("/assets/$path/-/link/entry/$entryId")
    } else {
        urlBuilder.path("/assets/$path/-/link")
    }

    attachVariantModifiers(urlBuilder, profile, height, width, mimeType, fit, gravity, rotate, flip, filter, blur, quality)
    val fetchUrl = urlBuilder.build()
    client.get(fetchUrl.fullPath).apply {
        status shouldBe expectedStatusCode
        headers[HttpHeaders.Location] shouldBe null
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

suspend fun fetchAssetInfo(
    client: HttpClient,
    path: String,
    entryId: Long? = null,
    expectedStatus: HttpStatusCode = HttpStatusCode.OK,
): AssetResponse? {
    return if (entryId != null) {
        "/assets/$path/-/metadata/entry/$entryId"
    } else {
        "/assets/$path/-/metadata"
    }.let { requestPath ->
        val response = client.get(requestPath)
        response.status shouldBe expectedStatus

        if (response.status == HttpStatusCode.NotFound) {
            null
        } else {
            response.body<AssetResponse>().apply {
                entryId shouldBe entryId
            }
        }
    }
}

suspend fun fetchAssetsInfo(
    client: HttpClient,
    path: String,
    limit: Int = 1,
    expectedStatus: HttpStatusCode = HttpStatusCode.OK,
): List<AssetResponse> {
    val requestPath = "/assets/$path/-/metadata/$limit"
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
}
