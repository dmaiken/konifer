package io.konifer.entrypoint

import io.konifer.application.usecase.delete.DeleteAssetUseCase
import io.konifer.application.usecase.fetch.FetchAssetHandler
import io.konifer.application.usecase.store.AssetAndLocation
import io.konifer.application.usecase.store.StoreNewAssetUseCase
import io.konifer.application.usecase.update.UpdateAssetUseCase
import io.konifer.common.http.AssetEntriesResponse
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.ErrorCode
import io.konifer.common.http.ErrorResponse
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.selector.ReturnFormat
import io.konifer.domain.context.InvalidRequestException
import io.konifer.domain.context.RequestContextFactory
import io.konifer.infrastructure.http.AssetUrlGenerator
import io.konifer.infrastructure.http.cache.appendAssetCacheControlHeaders
import io.konifer.infrastructure.http.fromAsset
import io.konifer.infrastructure.http.fromAssetData
import io.konifer.infrastructure.http.getAppStatusCacheHeader
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.RequestConnectionPoint
import io.ktor.server.application.Application
import io.ktor.server.plugins.origin
import io.ktor.server.request.contentType
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.debug
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject

private val logger = KtorSimpleLogger("io.konifer.entrypoint.AssetRouting")

const val ASSET_PATH_PREFIX = "/assets"

@OptIn(ExperimentalKtorApi::class)
fun Application.configureAssetRouting() {
    logger.info("Initializing asset routes")
    val fetchAssetHandler by inject<FetchAssetHandler>()
    val deleteAssetUseCase by inject<DeleteAssetUseCase>()
    val updateAssetUseCase by inject<UpdateAssetUseCase>()
    val storeNewAssetUseCase by inject<StoreNewAssetUseCase>()
    val assetUrlGenerator by inject<AssetUrlGenerator>()
    val requestContextFactory by inject<RequestContextFactory>()

    routing {
        route(ASSET_PATH_PREFIX) {
            get("/{...}") {
                val requestContext =
                    requestContextFactory.fromFetchRequest(
                        path = call.request.path(),
                        headers = call.request.headers,
                        queryParameters = call.parameters,
                        origin = call.request.origin,
                    )
                call.appendAssetCacheControlHeaders(requestContext.pathConfiguration.cacheControl)

                logger.debug {
                    "Fetching asset (limit: ${requestContext.selectors.limit}) with path (${requestContext.selectors.returnFormat}): ${requestContext.path}"
                }
                when (requestContext.selectors.returnFormat) {
                    ReturnFormat.INFO -> {
                        fetchAssetHandler.fetchMetadataByPath(requestContext)?.let { response ->
                            getAppStatusCacheHeader(response.cacheResult).let {
                                call.response.headers.append(it.first, it.second)
                            }
                            call.respond(HttpStatusCode.OK, AssetResponse.fromAssetData(response.asset))
                        } ?: call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found", ErrorCode.NOT_FOUND))
                    }

                    ReturnFormat.ENTRIES -> {
                        fetchAssetHandler
                            .fetchMetadataAtPath(requestContext)
                            .map { AssetResponse.fromAssetData(it) }
                            .let { call.respond(HttpStatusCode.OK, AssetEntriesResponse(it)) }
                    }

                    ReturnFormat.REDIRECT -> {
                        fetchAssetHandler.fetchRedirectByPath(requestContext)?.let { response ->
                            call.response.headers.append(HttpHeaders.Location, response.deliveryUrl.url.toString())
                            getAppStatusCacheHeader(response.cacheResult).let {
                                call.response.headers.append(it.first, it.second)
                            }
                            call.respond(HttpStatusCode.TemporaryRedirect)
                        } ?: call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found", ErrorCode.NOT_FOUND))
                    }

                    ReturnFormat.LINK -> {
                        fetchAssetHandler.fetchLinkByPath(requestContext)?.let { response ->
                            getAppStatusCacheHeader(response.cacheResult).let {
                                call.response.headers.append(it.first, it.second)
                            }
                            call.respond(HttpStatusCode.OK, response.toResponse())
                        } ?: call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found", ErrorCode.NOT_FOUND))
                    }

                    ReturnFormat.CONTENT, ReturnFormat.DOWNLOAD -> {
                        fetchAssetHandler.fetchContentByPath(requestContext)?.let { content ->
                            call.respondContent(
                                content = content,
                                returnFormat = requestContext.selectors.returnFormat,
                            )
                        } ?: call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found", ErrorCode.NOT_FOUND))
                    }
                }
            }.describe { fetchAssetDocumentation() }

            post {
                call.storeNewAsset(assetUrlGenerator, storeNewAssetUseCase)
            }.describe { storeAssetDocumentation(atRoot = true) }

            post("/{...}") {
                call.storeNewAsset(assetUrlGenerator, storeNewAssetUseCase)
            }.describe { storeAssetDocumentation(atRoot = false) }

            put("/{...}") {
                val requestContext = requestContextFactory.fromUpdateRequest(call.request.path())
                logger.debug {
                    "Received request to update asset ${requestContext.path}:${requestContext.entryId}"
                }
                val asset =
                    updateAssetUseCase.updateAsset(
                        context = requestContext,
                        request = call.receive(StoreAssetRequest::class),
                    )
                call.respond(HttpStatusCode.OK, AssetResponse.fromAsset(asset.asset))
            }.describe { updateAssetDocumentation() }

            delete("/{...}") {
                deleteAssetUseCase.deleteAssets(
                    requestContextFactory.fromDeleteRequest(call.request.path(), call.parameters),
                )

                call.respond(HttpStatusCode.NoContent)
            }.describe { deleteAssetDocumentation() }
        }
    }
}

private suspend fun RoutingCall.storeNewAsset(
    assetUrlGenerator: AssetUrlGenerator,
    storeNewAssetUseCase: StoreNewAssetUseCase,
) {
    when (request.contentType().withoutParameters()) {
        ContentType.MultiPart.FormData -> {
            logger.debug { "Received multipart request to store a new asset" }
            storeMultipartAsset(
                storeNewAssetUseCase = storeNewAssetUseCase,
            ).let { asset ->
                respondStoredAsset(assetUrlGenerator, asset, request.origin)
            }
        }

        ContentType.Application.Json -> {
            logger.debug { "Received json request to store a new asset" }
            val payload = receive(StoreAssetRequest::class)
            val asset =
                storeNewAssetUseCase.handleFromExternalSource(
                    request = payload,
                    uriPath = request.path(),
                )
            respondStoredAsset(assetUrlGenerator, asset, request.origin)
        }

        else -> {
            respond(
                HttpStatusCode.UnsupportedMediaType,
                ErrorResponse("Use application/json or multipart/form-data", ErrorCode.UNSUPPORTED_MEDIA_TYPE),
            )
        }
    }
}

private suspend fun RoutingCall.storeMultipartAsset(storeNewAssetUseCase: StoreNewAssetUseCase): AssetAndLocation =
    receiveMultipartUpload { Json.decodeFromString<StoreAssetRequest>(it) }.use { upload ->
        when {
            upload.duplicateAssetReceived -> {
                throw InvalidRequestException("Multiple asset payloads supplied")
            }

            upload.duplicateMetadataReceived -> {
                throw InvalidRequestException("Multiple metadata payloads supplied")
            }

            !upload.request.isCompleted -> {
                throw InvalidRequestException("No asset metadata supplied")
            }

            upload.assetContainer == null -> {
                throw InvalidRequestException("No asset payload supplied")
            }

            else -> {
                storeNewAssetUseCase.handleFromUpload(
                    deferredRequest = upload.request,
                    multiPartContainer = upload.assetContainer,
                    uriPath = request.path(),
                )
            }
        }
    }

private suspend fun RoutingCall.respondStoredAsset(
    assetUrlGenerator: AssetUrlGenerator,
    asset: AssetAndLocation,
    origin: RequestConnectionPoint,
) {
    logger.info("Created asset under path: ${asset.locationPath}")

    response.headers.append(
        name = HttpHeaders.Location,
        value =
            assetUrlGenerator
                .generateAbsoluteLocationUrl(
                    path = asset.locationPath,
                    entryId = checkNotNull(asset.asset.entryId),
                    origin = origin,
                ).toString(),
    )
    respond(HttpStatusCode.Created, AssetResponse.fromAsset(asset.asset))
}
