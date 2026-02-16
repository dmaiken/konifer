package io.konifer.infrastructure.http.signature

import io.konifer.infrastructure.http.exception.ErrorResponse
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond

val HmacSignatureVerification =
    createRouteScopedPlugin(
        name = "HmacSignatureVerification",
        createConfiguration = ::SignatureConfig,
    ) {
        val verifier = HmacSignatureVerifier(pluginConfig)

        onCall { call ->
            if (call.request.httpMethod != HttpMethod.Get) {
                return@onCall
            }

            val isValid =
                try {
                    verifier.validateSignature(
                        path = call.request.path(),
                        parameters = call.request.queryParameters,
                    )
                } catch (_: MissingSignatureException) {
                    call.respond(
                        status = HttpStatusCode.Forbidden,
                        message = ErrorResponse("Missing signature"),
                    )
                    return@onCall
                }

            if (!isValid) {
                call.respond(
                    status = HttpStatusCode.Forbidden,
                    message = ErrorResponse("Invalid signature"),
                )
                return@onCall
            }
        }
    }
