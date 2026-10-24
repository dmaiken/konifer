package io.konifer.asset.fetch

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import io.konifer.BaseFunctionalTest
import io.konifer.ImageFactory
import io.konifer.common.http.AssetLinkResponse
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.ports.ObjectStore
import io.konifer.domain.ports.PathConfigurationRepository
import io.konifer.domain.transformation.toDimension
import io.konifer.domain.variant.OnDemandVariantMode
import io.konifer.domain.variant.OnDemandVariantProperties
import io.konifer.domain.variant.TransformationLimitProperties
import io.konifer.infrastructure.datastore.inmemory.InMemoryAssetRepository
import io.konifer.infrastructure.http.APP_CACHE_STATUS
import io.konifer.infrastructure.http.signature.HmacSigningAlgorithm
import io.konifer.infrastructure.http.signature.UrlSigner
import io.konifer.matchers.shouldBeSuccessful
import io.konifer.testInMemory
import io.konifer.util.storeAssetMultipartSource
import io.konifer.util.updateAsset
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import org.awaitility.Awaitility
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.koin.dsl.module
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class DeliveryUrlTest : BaseFunctionalTest() {
    @ParameterizedTest
    @ValueSource(strings = ["enabled", "profile_only", "disabled"])
    fun `expanded profile URLs survive eviction under every variant mode`(mode: String) {
        val uploads = AtomicInteger()
        lateinit var repository: ObservedAssetRepository
        lateinit var objectStore: ObjectStore
        val observedModule =
            module {
                single<AssetRepository> {
                    objectStore = get()
                    ObservedAssetRepository(InMemoryAssetRepository(objectStore)).apply {
                        repository = this
                        afterVariantUpload = {
                            uploads.incrementAndGet()
                            Unit
                        }
                    }
                }
            }
        testInMemory(
            """
            variant-profiles {
              thumbnail {
                width = 100
                format = webp
                pad = 2
              }
            }
            paths {
              "/**" {
                transform {
                  on-demand-variant.mode = $mode
                  eager-variants = [thumbnail]
                }
              }
            }
            """.trimIndent(),
            modules = listOf(observedModule),
        ) {
            val (image, _) = ImageFactory.testImage()
            val stored = storeAssetMultipartSource(client, image, StoreAssetRequest(), path = "profile")
            Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted { uploads.get() shouldBe 1 }
            val linkResponse = client.get("/assets/profile/-/link?profile=thumbnail")
            linkResponse.status shouldBe HttpStatusCode.OK
            val link = linkResponse.body<AssetLinkResponse>()
            val url = Url(link.url)
            url.parameters["profile"] shouldBe if (mode == "profile_only") "thumbnail" else null
            url.parameters["width"] shouldBe "100"
            url.parameters["format"] shouldBe "webp"
            url.parameters["pad"] shouldBe "2"
            link.attributes.width shouldBe 104
            val before = client.get(link.url)
            before.status shouldBe HttpStatusCode.OK
            // Remove both cached records and bytes, as retention eviction does.
            val asset = checkNotNull(repository.fetchForUpdate("/profile", stored.second!!.entryId))
            val evicted = asset.variants.filterNot { it.isOriginalVariant }
            asset.variants.removeAll(evicted.toSet())
            evicted.forEach { objectStore.delete(it.objectStoreBucket, it.objectStoreKey) }
            konifer()
                .fetchAssetInfo("profile")
                .shouldBeSuccessful()
                .body.variants shouldHaveSize 1
            val replayed = client.get(link.url)
            replayed.status shouldBe HttpStatusCode.OK
            replayed.headers[APP_CACHE_STATUS] shouldBe "miss"
            replayed.bodyAsBytes().toList() shouldBe before.bodyAsBytes().toList()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["service", "presigned"])
    fun `negotiated links pin format and entry without retaining labels`(strategy: String) =
        testInMemory(
            """
            paths {
              "/**" {
                delivery.strategy = $strategy
                transform.on-demand-variant.mode = profile_only
              }
            }
            variant-profiles.thumbnail.width = 100
            """.trimIndent(),
        ) {
            val (image, _) = ImageFactory.testImage()
            val stored = storeAssetMultipartSource(client, image, StoreAssetRequest(labels = mapOf("kind" to "old")), path = "profile")
            val response = client.get("/assets/profile/-/link?profile=thumbnail&kind=old") { header(HttpHeaders.Accept, "image/webp") }
            response.status shouldBe HttpStatusCode.OK
            val link = response.body<AssetLinkResponse>()
            val url = Url(link.url)
            url.parameters["format"] shouldBe "webp"
            url.parameters["kind"] shouldBe null
            url.encodedPath shouldBe "/assets/profile/-/entry/${stored.second!!.entryId}/content"
            val originalResponse = client.get(link.url)
            originalResponse.status shouldBe HttpStatusCode.OK
            originalResponse.headers[HttpHeaders.ContentType] shouldBe ImageFormat.WEBP.mimeType
            updateAsset(client, stored.first[HttpHeaders.Location]!!, StoreAssetRequest(labels = mapOf("kind" to "changed")))
            storeAssetMultipartSource(client, image, StoreAssetRequest(labels = mapOf("kind" to "old")), path = "profile")
            val replayed = client.get(link.url) { header(HttpHeaders.Accept, "image/png") }
            replayed.status shouldBe HttpStatusCode.OK
            replayed.bodyAsBytes().toList() shouldBe originalResponse.bodyAsBytes().toList()
            client.get(link.url.replace("width=100", "width=101")).status shouldBe HttpStatusCode.BadRequest
        }

    @ParameterizedTest
    @EnumSource(HmacSigningAlgorithm::class)
    fun `signed redirects can be followed without a signing client or Accept header`(algorithm: HmacSigningAlgorithm) =
        testInMemory(
            """
            url-signing {
              enabled = true
              secret-key = secret
              algorithm = ${algorithm.name.lowercase()}
            }
            """.trimIndent(),
        ) {
            configureClient { followRedirects = false }
            val (image, _) = ImageFactory.testImage()
            storeAssetMultipartSource(client, image, StoreAssetRequest(), path = "profile", verifyLocationHeader = false)
            val path = "/assets/profile/-/redirect"
            val signature = UrlSigner.sign(path, mapOf("width" to "100"), "secret", algorithm)
            val response = client.get("$path?width=100&s=$signature") { header(HttpHeaders.Accept, "image/webp") }
            response.status shouldBe HttpStatusCode.TemporaryRedirect
            val location = response.headers[HttpHeaders.Location]!!
            Url(location).parameters["format"] shouldBe "webp"
            Url(location).parameters.getAll("s")!!.size shouldBe 1
            val content = client.get(location)
            content.status shouldBe HttpStatusCode.OK
            content.headers[HttpHeaders.ContentType] shouldBe ImageFormat.WEBP.mimeType
        }

    @ParameterizedTest
    @ValueSource(strings = ["png", "pixels"])
    fun `orientation and full rendering parameters are reproducible after eviction`(format: String) =
        testInMemory(
            """
            paths {
              "/**" {
                transform.retention.cache.max-variants = 1
              }
            }
            """.trimIndent(),
        ) {
            val source = ImageFactory.testImage(ImageFormat.PNG).bytes
            val image = ByteArrayOutputStream()
            Vips.run { arena ->
                VImage.newFromBytes(arena, source).set("orientation", 6).writeToStream(image, ".jpeg")
            }
            storeAssetMultipartSource(client, image.toByteArray(), StoreAssetRequest(), path = "oriented")
            val pixels = if (format == "pixels") "&pixel-channels=argb&pixel-layout=planar" else ""
            val response =
                client.get(
                    "/assets/oriented/-/link?width=100&rotate=auto&format=$format&pad=2&pad-color=%2312345678&strip=exif,icc&filter=sepia&blur=1&quality=75&color-space=srgb$pixels",
                )
            response.status shouldBe HttpStatusCode.OK
            val link = response.body<AssetLinkResponse>()
            Url(link.url).parameters["rotate"] shouldBe "auto"
            Url(link.url).parameters["width"] shouldBe "100"
            Url(link.url).parameters["pad-color"] shouldBe "#12345678"
            val before = client.get(link.url)
            before.status shouldBe HttpStatusCode.OK
            client.get("/assets/oriented/-/content?width=101&format=png").status shouldBe HttpStatusCode.OK
            konifer()
                .fetchAssetInfo("oriented")
                .shouldBeSuccessful()
                .body.variants shouldHaveSize 2
            val after = client.get(link.url)
            after.status shouldBe HttpStatusCode.OK
            after.headers[APP_CACHE_STATUS] shouldBe "miss"
            after.bodyAsBytes().toList() shouldBe before.bodyAsBytes().toList()
        }

    @Test
    fun `cached delivery URLs still obey current generation policies and limits`() {
        val current = AtomicReference(PathConfiguration.default)
        val policies =
            module {
                single<PathConfigurationRepository> {
                    object : PathConfigurationRepository {
                        override fun fetch(path: String): PathConfiguration = current.get()
                    }
                }
            }
        testInMemory(modules = listOf(policies)) {
            val (image, _) = ImageFactory.testImage()
            storeAssetMultipartSource(client, image, StoreAssetRequest(), path = "profile")
            val response = client.get("/assets/profile/-/link?width=100")
            response.status shouldBe HttpStatusCode.OK
            val link = response.body<AssetLinkResponse>()
            client.get(link.url).headers[APP_CACHE_STATUS] shouldBe "hit"
            listOf(OnDemandVariantMode.DISABLED, OnDemandVariantMode.PROFILE_ONLY).forEach { mode ->
                current.set(
                    PathConfiguration.default.copy(
                        transform =
                            PathConfiguration.default.transform.copy(
                                onDemandVariant = OnDemandVariantProperties(mode = mode),
                            ),
                    ),
                )
                client.get(link.url).status shouldBe HttpStatusCode.BadRequest
            }
            current.set(
                PathConfiguration.default.copy(
                    transform =
                        PathConfiguration.default.transform.copy(
                            limits = TransformationLimitProperties(maxWidth = 10.toDimension()),
                        ),
                ),
            )
            client.get(link.url).status shouldBe HttpStatusCode.BadRequest
            current.set(PathConfiguration.default)
            val restored = client.get(link.url)
            restored.status shouldBe HttpStatusCode.OK
            restored.headers[APP_CACHE_STATUS] shouldBe "hit"
        }
    }

    @Test
    fun `original delivery URLs work even when source exceeds transformation limits`() =
        testInMemory(
            """
            paths {
              "/**" {
                transform {
                  on-demand-variant.mode = disabled
                  limits.max-width = 10
                }
              }
            }
            """.trimIndent(),
        ) {
            val (image, _) = ImageFactory.testImage()
            storeAssetMultipartSource(client, image, StoreAssetRequest(), path = "profile")
            val link = client.get("/assets/profile/-/link").body<AssetLinkResponse>()
            val response = client.get(link.url)
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsBytes().toList() shouldBe image.toList()
        }
}
