package io.direkt.infrastructure.http.signature

enum class HmacSigningAlgorithm(
    val value: String,
) {
    HMAC_SHA256("HmacSHA256"),
    HMAC_SHA384("HmacSHA384"),
    HMAC_SHA512("HmacSHA512"),
    ;

    companion object Factory {
        fun fromConfig(value: String): HmacSigningAlgorithm =
            when (value.lowercase()) {
                "hmac_sha256" -> HMAC_SHA256
                "hmac_sha384" -> HMAC_SHA384
                "hmac_sha512" -> HMAC_SHA512
                else -> throw IllegalArgumentException("Unsupported HMAC signing algorithm: $value")
            }
    }
}
