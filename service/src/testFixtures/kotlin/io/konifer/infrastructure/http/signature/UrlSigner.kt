package io.konifer.infrastructure.http.signature

import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object UrlSigner {
    /**
     * Generates a fully qualified, HMAC-signed URL.
     *
     * @param path The resource path (e.g., "/images/logo.png")
     * @param params Map of query parameters (e.g., "width" to "500")
     * @param secretKey The shared secret key
     * @return The final signed URL string
     */
    fun sign(
        path: String,
        params: Map<String, String>,
        secretKey: String,
        algorithm: HmacSigningAlgorithm,
        sortParameters: Boolean = true,
    ): String {
        val sortedParams =
            if (sortParameters) {
                // Sort params by key to ensure "width=100&height=200" equals "height=200&width=100"
                params.toSortedMap()
            } else {
                params.toSortedMap().reversed()
            }

        val queryString = sortedParams.entries.joinToString("&") { (k, v) -> "$k=$v" }
        val payload = if (queryString.isNotEmpty()) "$path?$queryString" else path

        val mac = Mac.getInstance(algorithm.value)
        mac.init(SecretKeySpec(secretKey.toByteArray(StandardCharsets.UTF_8), algorithm.value))
        val signatureBytes = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))

        val signature = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes)

        return signature
    }
}
