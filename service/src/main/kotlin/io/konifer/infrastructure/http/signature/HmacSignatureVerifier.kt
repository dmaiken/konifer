package io.konifer.infrastructure.http.signature

import io.ktor.http.Parameters
import java.security.MessageDigest

class HmacSignatureVerifier(
    signatureConfig: SignatureConfig,
) {
    private val signatureParam = signatureConfig.signatureParam
    private val signer = HmacUrlSigner(signatureConfig)

    fun validateSignature(
        path: String,
        parameters: Parameters,
    ): Boolean {
        val received = parameters[signatureParam]
        if (received.isNullOrBlank()) throw MissingSignatureException()
        val expected = signer.sign(path, parameters)
        return MessageDigest.isEqual(received.toByteArray(), expected.toByteArray())
    }
}
