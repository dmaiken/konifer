package io.direkt.asset

import io.direkt.config.testInMemory
import io.direkt.infrastructure.StoreAssetRequest
import io.direkt.infrastructure.http.signature.HmacSigningAlgorithm
import io.direkt.infrastructure.http.signature.UrlSigner
import io.direkt.util.createJsonClient
import io.direkt.util.fetchAssetLink
import io.direkt.util.storeAssetMultipartSource
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class UrlSigningTest {
    @Test
    fun `signature is required when url-signing is enabled`() =
        testInMemory(
            """
            url-signing {
                enabled = true
                secret-key = secret
            }
            """.trimIndent(),
        ) {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request, path = "profile", verifyLocationHeader = false)

            fetchAssetLink(client, path = "profile", expectedStatusCode = HttpStatusCode.Forbidden)
        }

    @ParameterizedTest
    @EnumSource(HmacSigningAlgorithm::class)
    fun `can fetch asset with signature when enabled`(algorithm: HmacSigningAlgorithm) =
        testInMemory(
            """
            url-signing {
                enabled = true
                secret-key = secret
                algorithm = ${algorithm.name.lowercase()}
            }
            """.trimIndent(),
        ) {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request, path = "profile", verifyLocationHeader = false)

            val path = "/assets/profile/-/link"
            val paramMap =
                mapOf(
                    "h" to "100",
                    "w" to "200",
                )
            val signature =
                UrlSigner.sign(
                    path = path,
                    params = paramMap,
                    secretKey = "secret",
                    algorithm = algorithm,
                )
            fetchAssetLink(client, path = "profile", height = 100, width = 200, signature = signature)
        }

    @ParameterizedTest
    @EnumSource(HmacSigningAlgorithm::class)
    fun `cannot fetch asset with invalid signature when enabled`(algorithm: HmacSigningAlgorithm) =
        testInMemory(
            """
            url-signing {
                enabled = true
                secret-key = secret
                algorithm = ${algorithm.name.lowercase()}
            }
            """.trimIndent(),
        ) {
            val client = createJsonClient(followRedirects = false)
            val image = javaClass.getResourceAsStream("/images/joshua-tree/joshua-tree.png")!!.readBytes()
            val request =
                StoreAssetRequest(
                    alt = "an image",
                )
            storeAssetMultipartSource(client, image, request, path = "profile", verifyLocationHeader = false)

            val path = "/assets/profile/-/link"
            val paramMap =
                mapOf(
                    "h" to "100",
                    "w" to "200",
                )
            val signature =
                UrlSigner.sign(
                    path = path,
                    params = paramMap,
                    secretKey = "secretttt",
                    algorithm = algorithm,
                )
            fetchAssetLink(
                client,
                path = "profile",
                height = 100,
                width = 200,
                signature = signature,
                expectedStatusCode = HttpStatusCode.Forbidden,
            )
        }
}
