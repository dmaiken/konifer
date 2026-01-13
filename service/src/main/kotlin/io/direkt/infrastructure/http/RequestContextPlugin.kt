package io.direkt.infrastructure.http

import io.direkt.infrastructure.http.CustomAttributes.deleteRequestContextKey
import io.direkt.infrastructure.http.CustomAttributes.queryRequestContextKey
import io.direkt.infrastructure.http.CustomAttributes.updateRequestContextKey
import io.direkt.service.context.RequestContextFactory
import io.ktor.http.HttpMethod
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.koin.ktor.ext.getKoin

/**
 * Populates the request context on the call's attributes.
 */
val RequestContextPlugin =
    createRouteScopedPlugin(
        name = "RequestContext",
    ) {
        val requestContextFactory by application.getKoin().inject<RequestContextFactory>()

        onCall { call ->
            when (call.request.httpMethod) {
                HttpMethod.Get -> {
                    call.attributes[queryRequestContextKey] =
                        requestContextFactory.fromGetRequest(
                            path = call.request.path(),
                            queryParameters = call.parameters,
                        )
                }
                HttpMethod.Delete -> {
                    call.attributes[deleteRequestContextKey] =
                        requestContextFactory.fromDeleteRequest(
                            path = call.request.path(),
                            queryParameters = call.parameters,
                        )
                }
                HttpMethod.Put -> {
                    call.attributes[updateRequestContextKey] =
                        requestContextFactory.fromUpdateRequest(
                            path = call.request.path(),
                        )
                }
                // Skipping POST because its more complicated - we need to extract the mimeType of the asset content
            }
        }
    }
