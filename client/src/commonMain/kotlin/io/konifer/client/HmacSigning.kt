package io.konifer.client

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.SHA384
import dev.whyoleg.cryptography.algorithms.SHA512
import io.ktor.http.URLBuilder
import io.ktor.http.encodedPath
import kotlin.io.encoding.Base64

enum class HmacSigningAlgorithm {
    HMAC_SHA256,
    HMAC_SHA384,
    HMAC_SHA512,
}

internal fun HmacSigningAlgorithm.toAlgorithm() =
    when (this) {
        HmacSigningAlgorithm.HMAC_SHA256 -> SHA256
        HmacSigningAlgorithm.HMAC_SHA384 -> SHA384
        HmacSigningAlgorithm.HMAC_SHA512 -> SHA512
    }

data class HmacSigningConfig(
    val secretKey: String,
    val algorithm: HmacSigningAlgorithm = HmacSigningAlgorithm.HMAC_SHA256,
    val signatureParameter: String = "s",
) {
    init {
        require(secretKey.isNotEmpty()) { "HMAC signing secret key cannot be empty" }
        require(signatureParameter.isNotBlank()) { "HMAC signing signature parameter cannot be blank" }
    }
}

class KoniferUrlSigner private constructor(
    private val key: HMAC.Key,
    val signatureParameter: String,
) {
    companion object {
        /**
         * Suspends once to perform the heavy lifting of importing the native key material.
         */
        suspend fun create(config: HmacSigningConfig): KoniferUrlSigner {
            val key =
                CryptographyProvider.Default
                    .get(HMAC)
                    .keyDecoder(config.algorithm.toAlgorithm())
                    .decodeFromByteArray(
                        format = HMAC.Key.Format.RAW,
                        bytes = config.secretKey.encodeToByteArray(),
                    )

            return KoniferUrlSigner(key, config.signatureParameter)
        }
    }

    suspend fun sign(urlBuilder: URLBuilder): String {
        // Generating the signature instance from a cached key is fast and thread-safe
        val signatureGenerator = key.signatureGenerator()

        val canonicalQuery =
            urlBuilder.parameters
                .entries()
                .filter { it.key != signatureParameter }
                .sortedBy { it.key }
                .joinToString("&") { (key, values) ->
                    "$key=${values.first()}"
                }

        val path = urlBuilder.encodedPath.withLeadingSlash()
        val payload = if (canonicalQuery.isNotEmpty()) "$path?$canonicalQuery" else path

        return signatureGenerator
            .generateSignature(payload.encodeToByteArray())
            .base64UrlWithoutPadding()
    }
}

internal fun ByteArray.base64UrlWithoutPadding(): String =
    Base64.UrlSafe
        .withPadding(Base64.PaddingOption.ABSENT)
        .encode(this)

private fun String.withLeadingSlash() = if (startsWith("/")) this else "/$this"
