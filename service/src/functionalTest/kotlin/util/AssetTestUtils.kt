package util

import BaseTestcontainerTest.Companion.BOUNDARY
import asset.model.AssetResponse
import asset.model.StoreAssetRequest
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.fullPath
import io.ktor.http.path
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

suspend fun fetchAsset(
    client: HttpClient,
    path: String = "profile",
    entryId: Long? = null,
    height: Int? = null,
    width: Int? = null,
): ByteArray {
    val urlBuilder = URLBuilder()
    urlBuilder.path("/assets/$path")
    if (entryId != null) {
        urlBuilder.parameters.append("entryId", entryId.toString())
    }
    if (height != null) {
        urlBuilder.parameters.append("h", height.toString())
    }
    if (width != null) {
        urlBuilder.parameters.append("w", height.toString())
    }
    val url = urlBuilder.build()
    url.fullPath
    val fetchResponse =
        client.get(url.fullPath).apply {
            status shouldBe HttpStatusCode.TemporaryRedirect
            headers["Location"] shouldContain "http://"
        }
    val location = Url(fetchResponse.headers[HttpHeaders.Location]!!).fullPath
    val storeResponse = client.get(location)
    storeResponse.status shouldBe HttpStatusCode.OK

    return storeResponse.bodyAsBytes()
}

suspend fun fetchAssetInfo(
    client: HttpClient,
    path: String,
    entryId: Long? = null,
    expectedStatus: HttpStatusCode = HttpStatusCode.OK,
): AssetResponse? {
    return if (entryId != null) {
        "/assets/$path?return=metadata&entryId=$entryId"
    } else {
        "/assets/$path?return=metadata"
    }.let {
        val response = client.get("/assets/$path?return=metadata")
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
