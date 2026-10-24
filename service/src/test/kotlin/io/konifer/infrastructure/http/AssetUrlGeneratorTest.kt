package io.konifer.infrastructure.http

import io.konifer.createRequestedImageTransformation
import io.konifer.domain.context.HttpRequest
import io.konifer.domain.path.DeliveryProperties
import io.konifer.domain.path.DeliveryStrategy
import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.path.PreSignedProperties
import io.konifer.domain.path.TemplateProperties
import io.konifer.domain.ports.ObjectStore
import io.konifer.domain.ports.PresignedUrl
import io.konifer.domain.variant.projection.VariantDeliveryProjection
import io.konifer.infrastructure.HttpProperties
import io.konifer.infrastructure.http.signature.HmacSignatureVerifier
import io.konifer.infrastructure.http.signature.HmacSigningAlgorithm
import io.konifer.infrastructure.http.signature.SignatureConfig
import io.konifer.infrastructure.http.signature.UrlSigningProperties
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toKotlinInstant

class AssetUrlGeneratorTest {
    private val objectStore = mockk<ObjectStore>()

    @Test
    fun `service delivery uses the configured public url and resolved transformation`() =
        runTest {
            val generator = AssetUrlGenerator(HttpProperties(Url("https://images.example.com/root")), objectStore, UrlSigningProperties())
            val request = request(parameters = Parameters.build { append("width", "320") })

            val result =
                generator.generateDeliveryUrl(
                    path = "/profiles/avatar",
                    entryId = 42,
                    variant = variant(),
                    transformation = createRequestedImageTransformation(width = 320, height = 160),
                    request = request,
                    pathConfiguration = PathConfiguration.default,
                )

            result.expiresAt shouldBe null
            val url = result.url
            url.protocol shouldBe URLProtocol.HTTPS
            url.host shouldBe "images.example.com"
            url.encodedPath shouldBe "/root/assets/profiles/avatar/-/entry/42/content"
            url.parameters["width"] shouldBe "320"
            coVerify(exactly = 0) { objectStore.generatePresignedUrl(any(), any(), any()) }
        }

    @Test
    fun `service delivery uses the request origin when public url is not configured`() =
        runTest {
            val generator = AssetUrlGenerator(HttpProperties(), objectStore, UrlSigningProperties())
            val request = request(scheme = "https", host = "request.example.com", port = 8443)

            val result =
                generator.generateDeliveryUrl(
                    path = "/profile",
                    entryId = 7,
                    variant = variant(),
                    transformation = createRequestedImageTransformation(width = 320, height = 160),
                    request = request,
                    pathConfiguration = PathConfiguration.default,
                )

            result.url.encodedPath shouldBe "/assets/profile/-/entry/7/content"
            result.url.host shouldBe "request.example.com"
            result.url.port shouldBe 8443
        }

    @Test
    fun `asset location uses the request origin when public url is not configured`() {
        val generator = AssetUrlGenerator(HttpProperties(), objectStore, UrlSigningProperties())

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
            val generator = AssetUrlGenerator(HttpProperties(), objectStore, UrlSigningProperties())
            val variant = variant(bucket = "variant-bucket", key = "variants/image.webp")
            val delivery =
                DeliveryProperties(
                    strategy = DeliveryStrategy.PRESIGNED,
                    preSigned = PreSignedProperties(ttl = 15.minutes),
                )
            val presignedUrl = Url("https://objects.example.com/signed-image")
            val expiresAt = Instant.now().plus(1, java.time.temporal.ChronoUnit.HOURS)
            coEvery {
                objectStore.generatePresignedUrl("variant-bucket", "variants/image.webp", 15.minutes)
            } returns PresignedUrl.Supported(url = presignedUrl, expiresAt = expiresAt)

            val result =
                generator.generateDeliveryUrl(
                    path = "/profile",
                    entryId = 7,
                    variant = variant,
                    transformation = createRequestedImageTransformation(width = 320, height = 160),
                    request = request(),
                    pathConfiguration = PathConfiguration(deliveryProperties = delivery),
                )

            result.url shouldBe presignedUrl
            result.expiresAt shouldBe expiresAt.toKotlinInstant()
            coVerify(exactly = 1) {
                objectStore.generatePresignedUrl("variant-bucket", "variants/image.webp", 15.minutes)
            }
        }

    @Test
    fun `presigned delivery falls back to the service url when unsupported`() =
        runTest {
            val generator = AssetUrlGenerator(HttpProperties(), objectStore, UrlSigningProperties())
            val variant = variant(bucket = "variant-bucket", key = "variants/image.webp")
            val delivery = DeliveryProperties(strategy = DeliveryStrategy.PRESIGNED)
            coEvery { objectStore.generatePresignedUrl(any(), any(), any()) } returns PresignedUrl.NotSupported

            val result =
                generator.generateDeliveryUrl(
                    path = "/profile",
                    entryId = 7,
                    variant = variant,
                    transformation = createRequestedImageTransformation(width = 320, height = 160),
                    request = request(),
                    pathConfiguration = PathConfiguration(deliveryProperties = delivery),
                )

            result.url.encodedPath shouldBe "/assets/profile/-/entry/7/content"
            result.url.parameters["width"] shouldBe "320"
            result.expiresAt shouldBe null
        }

    @Test
    fun `template delivery resolves repeated bucket and key variables from the selected variant`() =
        runTest {
            val generator = AssetUrlGenerator(HttpProperties(), objectStore, UrlSigningProperties())
            val variant = variant(bucket = "variant-bucket", key = "variants/image.webp")
            val delivery =
                DeliveryProperties(
                    strategy = DeliveryStrategy.TEMPLATE,
                    template =
                        TemplateProperties(
                            string = "https://{bucket}.example.com/{bucket}/{key}?source={key}",
                        ),
                )

            val result =
                generator.generateDeliveryUrl(
                    path = "/profile",
                    entryId = 7,
                    variant = variant,
                    transformation = createRequestedImageTransformation(width = 320, height = 160),
                    request = request(),
                    pathConfiguration = PathConfiguration(deliveryProperties = delivery),
                )

            result.url.toString() shouldBe
                "https://variant-bucket.example.com/variant-bucket/variants/image.webp?source=variants/image.webp"
            coVerify(exactly = 0) { objectStore.generatePresignedUrl(any(), any(), any()) }
            result.expiresAt shouldBe null
        }

    @ParameterizedTest
    @EnumSource(HmacSigningAlgorithm::class)
    fun `service delivery signs destination path and expanded parameters without copying selectors`(algorithm: HmacSigningAlgorithm) =
        runTest {
            val signing = UrlSigningProperties(enabled = true, secretKey = "secret", algorithm = algorithm)
            val generator = AssetUrlGenerator(HttpProperties(Url("https://images.example.com/root")), objectStore, signing)
            val result =
                generator.generateDeliveryUrl(
                    path = "/profiles/avatar name",
                    entryId = 42,
                    variant = variant(),
                    transformation = createRequestedImageTransformation(width = 320, height = 160, pad = 2, padColor = "#12345678"),
                    request =
                        request(
                            parameters =
                                Parameters.build {
                                    append("width", "999")
                                    append("label:phone", "iphone")
                                    append("limit", "1")
                                    append("s", "stale-signature")
                                },
                        ),
                    pathConfiguration = PathConfiguration.default,
                )
            val url = result.url
            url.encodedPath shouldBe "/root/assets/profiles/avatar%20name/-/entry/42/content"
            url.parameters["width"] shouldBe "320"
            url.parameters["pad-color"] shouldBe "#12345678"
            url.parameters["label:phone"] shouldBe null
            url.parameters["limit"] shouldBe null
            url.parameters.getAll("s")!!.size shouldBe 1
            val verifier =
                HmacSignatureVerifier(
                    SignatureConfig().also {
                        it.secretKey = "secret"
                        it.algorithm = algorithm
                    },
                )
            verifier.validateSignature(url.encodedPath.removePrefix("/root"), url.parameters) shouldBe true
            verifier.validateSignature(url.encodedPath, url.parameters) shouldBe false
        }

    private fun variant(
        bucket: String = "assets",
        key: String = "image.png",
    ): VariantDeliveryProjection {
        val variant = mockk<VariantDeliveryProjection>()
        every { variant.objectStoreBucket } returns bucket
        every { variant.objectStoreKey } returns key

        return variant
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
