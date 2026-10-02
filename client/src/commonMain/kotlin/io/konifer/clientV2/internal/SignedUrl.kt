package io.konifer.clientV2.internal

import io.ktor.http.URLBuilder

internal suspend fun signedUrl(
    urlSigner: KoniferUrlSigner?,
    block: URLBuilder.() -> Unit,
): URLBuilder =
    URLBuilder()
        .apply(block)
        .apply {
            urlSigner?.let { signer ->
                parameters.append(signer.signatureParameter, signer.sign(this))
            }
        }
