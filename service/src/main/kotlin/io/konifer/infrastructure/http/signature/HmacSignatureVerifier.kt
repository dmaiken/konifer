package io.konifer.infrastructure.http.signature

import io.ktor.http.Parameters
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.component1
import kotlin.collections.component2

class HmacSignatureVerifier(
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

    fun validateSignature(
        path: String,
        parameters: Parameters,
    ): Boolean {
        val receivedSig = parameters[signatureParam]
        if (receivedSig.isNullOrBlank()) {
            throw MissingSignatureException()
        }

        // We strip the signature param from the parameters to get the original payload.
        // IMPORTANT: We sort parameters to ensure canonicalization (w=100&h=200 == h=200&w=100)
        val canonicalQuery =
            parameters
                .entries()
                .filter { it.key != signatureParam } // Remove the signature
                .sortedBy { it.key } // Sort by key to ensure consistency
                .joinToString("&") { (key, values) ->
                    // Re-encode values to ensure they match what was signed
                    "$key=${values.first()}"
                }
        val payload = if (canonicalQuery.isNotEmpty()) "$path?$canonicalQuery" else path

        val mac =
            Mac
                .getInstance(algorithm)
                .apply { init(secretKeySpec) }
        val computedBytes = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        val computedSig = Base64.getUrlEncoder().withoutPadding().encodeToString(computedBytes)

        // Never use `==` for signatures to avoid Timing Attacks
        return MessageDigest.isEqual(receivedSig.toByteArray(), computedSig.toByteArray())
    }
}
