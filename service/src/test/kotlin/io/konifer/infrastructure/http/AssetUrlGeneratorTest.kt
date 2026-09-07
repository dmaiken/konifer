package io.konifer.infrastructure.http

import io.konifer.domain.asset.AssetData
import io.konifer.domain.context.HttpRequest
import io.konifer.domain.path.DeliveryProperties
import io.konifer.domain.path.DeliveryStrategy
import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.path.PreSignedProperties
import io.konifer.domain.path.TemplateProperties
import io.konifer.domain.ports.ObjectStore
import io.konifer.domain.ports.PresignedUrl
import io.konifer.domain.variant.VariantData
import io.konifer.infrastructure.HttpProperties
import io.kotest.matchers.shouldBe
import io.ktor.http.Parameters
import io.ktor.http.RequestConnectionPoint
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.toKotlinLocalDateTime
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes

class AssetUrlGeneratorTest {
    private val objectStore = mockk<ObjectStore>()

    @Test
    fun `service delivery uses the configured public url and preserves request parameters`() =
        runTest {
            val generator = AssetUrlGenerator(HttpProperties(Url("https://images.example.com/root")), objectStore)
            val asset = assetData(path = "/profiles/avatar", entryId = 42)
            val request = request(parameters = Parameters.build { append("w", "320") })

            val result = generator.generateDeliveryUrl(asset, request, PathConfiguration.default)

            result.expiresAt shouldBe null
            val url = result.url
            url.protocol shouldBe URLProtocol.HTTPS
            url.host shouldBe "images.example.com"
            url.encodedPath shouldBe "/root/assets/profiles/avatar/-/entry/42/content"
            url.parameters["w"] shouldBe "320"
            coVerify(exactly = 0) { objectStore.generatePresignedUrl(any(), any(), any()) }
        }

    @Test
    fun `service delivery uses the request origin when public url is not configured`() =
        runTest {
            val generator = AssetUrlGenerator(HttpProperties(), objectStore)
            val request = request(scheme = "https", host = "request.example.com", port = 8443)

            val result = generator.generateDeliveryUrl(assetData(), request, PathConfiguration.default)

            result.url.toString() shouldBe "https://request.example.com:8443/assets/profile/-/entry/7/content"
        }

    @Test
    fun `asset location uses the request origin when public url is not configured`() {
        val generator = AssetUrlGenerator(HttpProperties(), objectStore)

        val result =
            generator.generateAbsoluteLocationUrl(
                path = "/profiles/avatar",
                entryId = 42,
                origin = origin(scheme = "http", host = "localhost", port = 8080),
            )

        result.toString() shouldBe "http://localhost:8080/assets/profiles/avatar/-/entry/42"
    }

    @Test
    fun `presigned delivery returns the object store url`() =
        runTest {
            val generator = AssetUrlGenerator(HttpProperties(), objectStore)
            val asset = assetData(bucket = "variant-bucket", key = "variants/image.webp")
            val delivery =
                DeliveryProperties(
                    strategy = DeliveryStrategy.PRESIGNED,
                    preSigned = PreSignedProperties(ttl = 15.minutes),
                )
            val presignedUrl = Url("https://objects.example.com/signed-image")
            val expiresAt = LocalDateTime.now().plusHours(1)
            coEvery {
                objectStore.generatePresignedUrl("variant-bucket", "variants/image.webp", 15.minutes)
            } returns PresignedUrl.Supported(url = presignedUrl, expiresAt = expiresAt)

            val result = generator.generateDeliveryUrl(asset, request(), PathConfiguration(deliveryProperties = delivery))

            result.url shouldBe presignedUrl
            result.expiresAt shouldBe expiresAt.toKotlinLocalDateTime()
            coVerify(exactly = 1) {
                objectStore.generatePresignedUrl("variant-bucket", "variants/image.webp", 15.minutes)
            }
        }

    @Test
    fun `presigned delivery falls back to the service url when unsupported`() =
        runTest {
            val generator = AssetUrlGenerator(HttpProperties(), objectStore)
            val asset = assetData(bucket = "variant-bucket", key = "variants/image.webp")
            val delivery = DeliveryProperties(strategy = DeliveryStrategy.PRESIGNED)
            coEvery { objectStore.generatePresignedUrl(any(), any(), any()) } returns PresignedUrl.NotSupported

            val result = generator.generateDeliveryUrl(asset, request(), PathConfiguration(deliveryProperties = delivery))

            result.url.toString() shouldBe "http://localhost:8080/assets/profile/-/entry/7/content"
            result.expiresAt shouldBe null
        }

    @Test
    fun `template delivery resolves repeated bucket and key variables from the selected variant`() =
        runTest {
            val generator = AssetUrlGenerator(HttpProperties(), objectStore)
            val asset = assetData(bucket = "variant-bucket", key = "variants/image.webp")
            val delivery =
                DeliveryProperties(
                    strategy = DeliveryStrategy.TEMPLATE,
                    template =
                        TemplateProperties(
                            string = "https://{bucket}.example.com/{bucket}/{key}?source={key}",
                        ),
                )

            val result = generator.generateDeliveryUrl(asset, request(), PathConfiguration(deliveryProperties = delivery))

            result.url.toString() shouldBe
                "https://variant-bucket.example.com/variant-bucket/variants/image.webp?source=variants/image.webp"
            coVerify(exactly = 0) { objectStore.generatePresignedUrl(any(), any(), any()) }
            result.expiresAt shouldBe null
        }

    private fun assetData(
        path: String = "/profile",
        entryId: Long = 7,
        bucket: String = "assets",
        key: String = "image.png",
    ): AssetData {
        val variant = mockk<VariantData>()
        every { variant.objectStoreBucket } returns bucket
        every { variant.objectStoreKey } returns key

        val asset = mockk<AssetData>()
        every { asset.path } returns path
        every { asset.entryId } returns entryId
        every { asset.variants } returns listOf(variant)
        return asset
    }

    private fun request(
        parameters: Parameters = Parameters.Empty,
        scheme: String = "http",
        host: String = "localhost",
        port: Int = 8080,
    ): HttpRequest = HttpRequest(parameters, origin(scheme, host, port))

    private fun origin(
        scheme: String,
        host: String,
        port: Int,
    ): RequestConnectionPoint {
        val origin = mockk<RequestConnectionPoint>()
        every { origin.scheme } returns scheme
        every { origin.serverHost } returns host
        every { origin.serverPort } returns port
        return origin
    }
}
