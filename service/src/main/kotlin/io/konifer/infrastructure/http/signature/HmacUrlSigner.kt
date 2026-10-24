package io.konifer.infrastructure.http.signature

import io.ktor.http.Parameters
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.component1
import kotlin.collections.component2

class HmacUrlSigner(
    signatureConfig: SignatureConfig,
) {
    private val algorithm = signatureConfig.algorithm.value
    private val secretKey = signatureConfig.secretKey
    private val signatureParam = signatureConfig.signatureParam
    private val secretKeySpec =
        SecretKeySpec(
            secretKey.toByteArray(Charsets.UTF_8),
            algorithm,
        )

    init {
        if (secretKey.isEmpty()) {
            throw IllegalArgumentException("HmacSignatureVerification requires a secretKey")
        }
    }

    fun sign(
        path: String,
        parameters: Parameters,
    ): String {
        // We strip the signature param from the parameters to get the original payload.
        // IMPORTANT: We sort parameters to ensure canonicalization (width=100&height=200 == height=200&width=100)
        val canonicalQuery =
            parameters
                .entries()
                .filter { it.key != signatureParam } // Remove the signature
                .sortedBy { it.key } // Sort by key to ensure consistency
                .joinToString("&") { (key, values) ->
                    // Preserve the existing signing protocol: decoded values, first value per key.
                    "$key=${values.first()}"
                }
        val payload = if (canonicalQuery.isNotEmpty()) "$path?$canonicalQuery" else path

        val mac =
            Mac
                .getInstance(algorithm)
                .apply { init(secretKeySpec) }
        val computedBytes = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(computedBytes)
    }
}
