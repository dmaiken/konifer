package io.konifer.infrastructure.http.signature

import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.ktor.ext.inject

fun Application.configureSignatureVerification() {
    val settings by inject<UrlSigningProperties>()
    if (settings.enabled) {
        install(HmacSignatureVerification) {
            algorithm = settings.algorithm
            secretKey = settings.secretKey
            signatureParam = settings.signatureParam
        }
    }
}
