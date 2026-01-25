package io.konifer.infrastructure.http.signature

/*
 * Must be mutable with no-args constructor to be used as Ktor plugin configuration
 */
class SignatureConfig {
    var secretKey = ""
    var signatureParam = "s"
    var algorithm = HmacSigningAlgorithm.HMAC_SHA256
}
