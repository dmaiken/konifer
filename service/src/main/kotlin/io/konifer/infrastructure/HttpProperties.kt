package io.konifer.infrastructure

data class HttpProperties(
    val publicUrl: String,
) {
    companion object Factory {
        const val DEFAULT_PUBLIC_URL: String = "http://localhost"
    }

    init {
        require(publicUrl.startsWith("http://") || publicUrl.startsWith("https://")) {
            "public-url must start with http:// or https://"
        }
    }
}
