package io.konifer.client.assets.fetch

/** Selects how variant content is delivered to the client. */
enum class ContentDelivery {
    /** Stream content through Konifer. */
    THROUGH_KONIFER,

    /** Ask Konifer for a delivery URL, then follow its HTTP redirect. */
    FOLLOW_REDIRECT,
}
