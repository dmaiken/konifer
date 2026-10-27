package io.konifer.asset.fetch

import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory.testImage
import io.konifer.application.usecase.fetch.CacheResult
import io.konifer.application.usecase.fetch.FetchAssetHandler
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.konifer.domain.context.RequestContextFactory
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.ports.ObjectStore
import io.konifer.infrastructure.datastore.inmemory.InMemoryAssetRepository
import io.konifer.infrastructure.http.APP_CACHE_STATUS
import io.konifer.infrastructure.objectstore.inmemory.InMemoryObjectStore
import io.konifer.testInMemory
import io.konifer.util.storeAssetMultipartSource
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.toByteArray
import io.mockk.mockk
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.koin.dsl.module
import java.util.zip.GZIPInputStream

class FetchAssetStreamingTest : BaseFunctionalTest() {
    private lateinit var objectStore: ObservedObjectStore
    private lateinit var repository: ObservedAssetRepository
    private lateinit var handler: FetchAssetHandler
    private lateinit var contextFactory: RequestContextFactory
    private val observedModule =
        module {
            single<ObjectStore> {
                ObservedObjectStore(get<InMemoryObjectStore>()).also { objectStore = it }
            }
            single<AssetRepository> {
                ObservedAssetRepository(get<InMemoryAssetRepository>()).also { repository = it }
            }
            single<FetchAssetHandler> {
                contextFactory = get()
                FetchAssetHandler(get(), get(), get(), get()).also { handler = it }
            }
        }

    @Test
    fun `unknown fetch parameters do not filter assets or change the selected representation`() =
        testInMemory {
            val image = testImage().bytes
            val first = storeAssetMultipartSource(client, image, StoreAssetRequest(labels = mapOf("purpose" to "old")))
            val latest = storeAssetMultipartSource(client, image, StoreAssetRequest(labels = mapOf("purpose" to "new")))
            val info = client.get("/assets/profile/-/info?purpose=old&widht=900&label-purpose=old")
            info.status shouldBe HttpStatusCode.OK
            info.body<AssetResponse>().entryId shouldBe latest.second!!.entryId
            client.get("/assets/profile/-/info?label:purpose=old").body<AssetResponse>().entryId shouldBe first.second!!.entryId

            val original = client.get("/assets/profile/-/content?width=100")
            val unknown = client.get("/assets/profile/-/content?width=100&widht=900&purpose=old")
            unknown.status shouldBe HttpStatusCode.OK
            unknown.headers[HttpHeaders.ETag] shouldBe original.headers[HttpHeaders.ETag]
            unknown.bodyAsBytes().toList() shouldBe original.bodyAsBytes().toList()
        }

    @ParameterizedTest
    @ValueSource(strings = ["content", "download"])
    fun `conditional requests distinguish negotiated formats and transformations`(returnFormat: String) =
        testInMemory(modules = listOf(observedModule)) {
            storeAssetMultipartSource(client, testImage(ImageFormat.JPEG).bytes, StoreAssetRequest())
            val representations = listOf("image/png" to 100, "image/webp" to 100, "image/webp" to 101)
            val responses =
                representations.map { (format, width) ->
                    client
                        .get("/assets/profile/-/$returnFormat?width=$width") { header(HttpHeaders.Accept, format) }
                        .also { it.status shouldBe HttpStatusCode.OK }
                }
            val etags = responses.map { checkNotNull(it.headers[HttpHeaders.ETag]) }
            etags.toSet().size shouldBe representations.size
            etags.forEach { it shouldStartWith "\"" }
            representations.forEachIndexed { index, (format, width) ->
                objectStore.reset()
                val previousTag = etags[(index + 1) % etags.size]
                val changed =
                    client.get("/assets/profile/-/$returnFormat?width=$width") {
                        header(HttpHeaders.Accept, format)
                        header(HttpHeaders.IfNoneMatch, previousTag)
                        header(HttpHeaders.IfModifiedSince, checkNotNull(responses[index].headers[HttpHeaders.LastModified]))
                    }
                changed.status shouldBe HttpStatusCode.OK
                changed.headers[HttpHeaders.ContentType] shouldBe format
                changed.headers[HttpHeaders.ETag] shouldBe etags[index]
                changed.bodyAsBytes().toList() shouldBe responses[index].bodyAsBytes().toList()
                objectStore.fetches shouldHaveSize 1
            }
        }

    @ParameterizedTest
    @ValueSource(strings = ["content", "download"])
    fun `raw pixel validators remain weak across HTTP compression and skip cached reads`(returnFormat: String) =
        testInMemory(modules = listOf(observedModule)) {
            storeAssetMultipartSource(client, testImage().bytes, StoreAssetRequest())
            val path = "/assets/profile/-/$returnFormat?width=100&format=pixels"
            val identity = client.get(path)
            identity.status shouldBe HttpStatusCode.OK
            val etag = checkNotNull(identity.headers[HttpHeaders.ETag])
            etag shouldStartWith "W/\""
            val compressed = client.get(path) { header(HttpHeaders.AcceptEncoding, "gzip") }
            compressed.status shouldBe HttpStatusCode.OK
            compressed.headers[HttpHeaders.ContentEncoding] shouldBe "gzip"
            compressed.headers[HttpHeaders.ETag] shouldBe etag
            GZIPInputStream(compressed.bodyAsBytes().inputStream()).use { it.readBytes() }.toList() shouldBe identity.bodyAsBytes().toList()
            objectStore.reset()
            val conditional =
                client.get(path) {
                    header(HttpHeaders.AcceptEncoding, "gzip")
                    header(HttpHeaders.IfNoneMatch, etag)
                }
            conditional.status shouldBe HttpStatusCode.NotModified
            conditional.bodyAsBytes().size shouldBe 0
            objectStore.fetches shouldHaveSize 0
        }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `resolving original or cached content defers reading the selected object until writing`(transformed: Boolean) =
        testInMemory(modules = listOf(observedModule)) {
            storeAssetMultipartSource(client, testImage().bytes, StoreAssetRequest())
            val path = "/assets/profile/-/content"
            val query = if (transformed) "?width=100" else ""
            val response = client.get(path + query)
            response.status shouldBe HttpStatusCode.OK
            val expected = response.bodyAsBytes()
            objectStore.reset()
            repository.resetReads()

            val context =
                contextFactory.fromFetchRequest(
                    path = path,
                    headers = Headers.Empty,
                    queryParameters = Parameters.build { if (transformed) append("width", "100") },
                    origin = mockk(),
                )
            val content = checkNotNull(handler.fetchContentByPath(context))
            content.delivery.cacheResult shouldBe CacheResult.HIT
            val selected = content.delivery.selectedVariant
            selected.isOriginalVariant shouldBe !transformed
            if (transformed) {
                selected.attributes.width.value shouldBe 100
            }

            objectStore.fetches shouldHaveSize 0
            repository.assetForDeliveryReads.get() shouldBe 1
            repository.assetReads.get() shouldBe 0
            repository.collectionReads.get() shouldBe 0
            repository.variantReads.get() shouldBe 0

            val received =
                withTimeout(10_000) {
                    coroutineScope {
                        val channel = ByteChannel()
                        val writer = launch { content.writeTo(channel) }
                        channel.toByteArray().also { writer.join() }
                    }
                }
            received.contentEquals(expected) shouldBe true
            objectStore.fetches shouldContainExactly listOf(selected.objectStoreBucket to selected.objectStoreKey)
        }

    @ParameterizedTest
    @CsvSource(
        "content, ETag, If-None-Match",
        "content, Last-Modified, If-Modified-Since",
        "download, ETag, If-None-Match",
        "download, Last-Modified, If-Modified-Since",
    )
    fun `matching conditional requests skip reading cached content`(
        returnFormat: String,
        responseHeader: String,
        requestHeader: String,
    ) = testInMemory(modules = listOf(observedModule)) {
        storeAssetMultipartSource(client, testImage().bytes, StoreAssetRequest())
        val path = "/assets/profile/-/$returnFormat?width=100"
        val initial = client.get(path)
        initial.status shouldBe HttpStatusCode.OK
        initial.bodyAsBytes().isNotEmpty() shouldBe true
        val validator = checkNotNull(initial.headers[responseHeader])
        objectStore.reset()
        repository.resetReads()

        val conditional = client.get(path) { header(requestHeader, validator) }

        conditional.status shouldBe HttpStatusCode.NotModified
        conditional.bodyAsBytes().size shouldBe 0
        conditional.headers[APP_CACHE_STATUS] shouldBe "hit"
        objectStore.fetches shouldHaveSize 0
        repository.assetForDeliveryReads.get() shouldBe 1
        repository.assetReads.get() shouldBe 0
        repository.collectionReads.get() shouldBe 0
        repository.variantReads.get() shouldBe 0
    }
}
