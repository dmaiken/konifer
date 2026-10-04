package io.konifer.client

/** HMAC digest algorithms supported for signed Konifer URLs. */
enum class HmacSigningAlgorithm {
    /** HMAC with SHA-256. */
    HMAC_SHA256,

    /** HMAC with SHA-384. */
    HMAC_SHA384,

    /** HMAC with SHA-512. */
    HMAC_SHA512,
}
