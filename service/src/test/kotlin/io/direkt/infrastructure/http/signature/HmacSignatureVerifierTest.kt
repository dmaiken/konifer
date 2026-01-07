package io.direkt.infrastructure.http.signature

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.http.ParametersBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class HmacSignatureVerifierTest {
    val secret = "secretKey"

    @ParameterizedTest
    @EnumSource(HmacSigningAlgorithm::class)
    fun `can validate hmac signature`(algorithm: HmacSigningAlgorithm) {
        val path = "/assets/user/123/profile/-/link/created"
        val paramMap =
            mapOf(
                "h" to "100",
                "w" to "200",
            )
        val signature =
            UrlSigner.sign(
                path = path,
                params = paramMap,
                secretKey = secret,
                algorithm = algorithm,
            )
        val parameters =
            ParametersBuilder()
                .apply {
                    paramMap.forEach { (key, value) ->
                        append(key, value)
                    }
                    set("s", signature)
                }.build()

        val verifier =
            HmacSignatureVerifier(
                signatureConfig =
                    SignatureConfig().apply {
                        this.algorithm = algorithm
                        this.secretKey = secret
                    },
            )
        verifier.validateSignature(path, parameters) shouldBe true
    }

    @Test
    fun `throws if secretKey is empty`() {
        shouldThrow<IllegalArgumentException> {
            HmacSignatureVerifier(
                signatureConfig =
                    SignatureConfig().apply {
                        this.algorithm = HmacSigningAlgorithm.HMAC_SHA256
                        this.secretKey = ""
                    },
            )
        }
    }

    @Test
    fun `throws if missing signature in query params`() {
        val path = "/assets/user/123/profile/-/link/created"
        val paramMap =
            mapOf(
                "h" to "100",
                "w" to "200",
            )
        val parameters =
            ParametersBuilder()
                .apply {
                    paramMap.forEach { (key, value) ->
                        append(key, value)
                    }
                }.build()

        val verifier =
            HmacSignatureVerifier(
                signatureConfig =
                    SignatureConfig().apply {
                        this.algorithm = HmacSigningAlgorithm.HMAC_SHA256
                        this.secretKey = secret
                    },
            )
        shouldThrow<MissingSignatureException> {
            verifier.validateSignature(path, parameters)
        }
    }

    @ParameterizedTest
    @EnumSource(HmacSigningAlgorithm::class)
    fun `invalid if secret is incorrect`(algorithm: HmacSigningAlgorithm) {
        val path = "/assets/user/123/profile/-/link/created"
        val paramMap =
            mapOf(
                "h" to "100",
                "w" to "200",
            )
        val signature =
            UrlSigner.sign(
                path = path,
                params = paramMap,
                secretKey = secret + "bad",
                algorithm = algorithm,
            )
        val parameters =
            ParametersBuilder()
                .apply {
                    paramMap.forEach { (key, value) ->
                        append(key, value)
                    }
                    set("s", signature)
                }.build()

        val verifier =
            HmacSignatureVerifier(
                signatureConfig =
                    SignatureConfig().apply {
                        this.algorithm = algorithm
                        this.secretKey = secret
                    },
            )
        verifier.validateSignature(path, parameters) shouldBe false
    }

    @ParameterizedTest
    @EnumSource(HmacSigningAlgorithm::class)
    fun `invalid if signature was signed without sorting query parameters`(algorithm: HmacSigningAlgorithm) {
        val path = "/assets/user/123/profile/-/link/created"
        val paramMap =
            mapOf(
                "h" to "100",
                "w" to "200",
            )
        val signature =
            UrlSigner.sign(
                path = path,
                params = paramMap,
                secretKey = secret,
                algorithm = algorithm,
                sortParameters = false,
            )
        val parameters =
            ParametersBuilder()
                .apply {
                    paramMap.forEach { (key, value) ->
                        append(key, value)
                    }
                    set("s", signature)
                }.build()

        val verifier =
            HmacSignatureVerifier(
                signatureConfig =
                    SignatureConfig().apply {
                        this.algorithm = algorithm
                        this.secretKey = secret
                    },
            )
        verifier.validateSignature(path, parameters) shouldBe false
    }

    @ParameterizedTest
    @EnumSource(HmacSigningAlgorithm::class)
    fun `invalid if query parameters are incorrect`(algorithm: HmacSigningAlgorithm) {
        val path = "/assets/user/123/profile/-/link/created"
        val paramMap =
            mapOf(
                "h" to "100",
                "w" to "200",
            )
        val signature =
            UrlSigner.sign(
                path = path,
                params = paramMap,
                secretKey = secret,
                algorithm = algorithm,
            )
        val parameters =
            ParametersBuilder()
                .apply {
                    paramMap.forEach { (key, value) ->
                        append(key, value)
                    }
                    set("bad", "param")
                    set("s", signature)
                }.build()

        val verifier =
            HmacSignatureVerifier(
                signatureConfig =
                    SignatureConfig().apply {
                        this.algorithm = algorithm
                        this.secretKey = secret
                    },
            )
        verifier.validateSignature(path, parameters) shouldBe false
    }

    @ParameterizedTest
    @EnumSource(HmacSigningAlgorithm::class)
    fun `invalid if path is incorrect`(algorithm: HmacSigningAlgorithm) {
        val path = "/assets/user/123/profile/-/link/created"
        val paramMap =
            mapOf(
                "h" to "100",
                "w" to "200",
            )
        val signature =
            UrlSigner.sign(
                path = path,
                params = paramMap,
                secretKey = secret,
                algorithm = algorithm,
            )
        val parameters =
            ParametersBuilder()
                .apply {
                    paramMap.forEach { (key, value) ->
                        append(key, value)
                    }
                    set("s", signature)
                }.build()

        val verifier =
            HmacSignatureVerifier(
                signatureConfig =
                    SignatureConfig().apply {
                        this.algorithm = algorithm
                        this.secretKey = secret
                    },
            )
        verifier.validateSignature(path + "bad-path", parameters) shouldBe false
    }
}
