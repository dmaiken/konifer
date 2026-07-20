package io.konifer.entrypoint

import io.konifer.application.usecase.delete.DeleteAssetUseCase
import io.konifer.application.usecase.fetch.FetchAssetHandler
import io.konifer.application.usecase.store.AssetAndLocation
import io.konifer.application.usecase.store.StoreNewAssetUseCase
import io.konifer.application.usecase.update.UpdateAssetUseCase
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.selector.ReturnFormat
import io.konifer.domain.asset.AssetDataContainer
import io.konifer.infrastructure.http.AssetUrlGenerator
import io.konifer.infrastructure.http.CustomAttributes.deleteRequestContextKey
import io.konifer.infrastructure.http.CustomAttributes.queryRequestContextKey
import io.konifer.infrastructure.http.CustomAttributes.updateRequestContextKey
import io.konifer.infrastructure.http.RequestContextPlugin
import io.konifer.infrastructure.http.cache.AssetCacheControlPlugin
import io.konifer.infrastructure.http.fromAsset
import io.konifer.infrastructure.http.fromAssetData
import io.konifer.infrastructure.http.getAppStatusCacheHeader
import io.konifer.infrastructure.http.getContentDispositionHeader
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.Application
import io.ktor.server.request.contentType
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import kotlin.coroutines.cancellation.CancellationException

private val logger = KtorSimpleLogger("io.konifer.entrypoint.AssetRouting")

const val ASSET_PATH_PREFIX = "/assets"

private const val METADATA_PART_NAME = "metadata"
private const val ASSET_PART_NAME = "asset"

fun Application.configureAssetRouting(maxMultipartContentLength: Long) {
    logger.info("Initializing asset routes")
    val fetchAssetHandler by inject<FetchAssetHandler>()
    val deleteAssetUseCase by inject<DeleteAssetUseCase>()
    val updateAssetUseCase by inject<UpdateAssetUseCase>()
    val storeNewAssetUseCase by inject<StoreNewAssetUseCase>()
    val assetUrlGenerator by inject<AssetUrlGenerator>()

    routing {
        route(ASSET_PATH_PREFIX) {
            install(RequestContextPlugin)
            install(AssetCacheControlPlugin)

            get("/{...}") {
                val requestContext = call.attributes[queryRequestContextKey]

                logger.info(
                    "Fetching asset (limit: ${requestContext.selectors.limit}) with path (${requestContext.selectors.returnFormat}): ${requestContext.path}",
                )
                when (requestContext.selectors.returnFormat) {
                    ReturnFormat.INFO -> {
                        if (requestContext.selectors.limit == 1) {
                            fetchAssetHandler.fetchMetadataByPath(requestContext, generateVariant = false)?.let { response ->
                                getAppStatusCacheHeader(response.cacheHit).let {
                                    call.response.headers.append(it.first, it.second)
                                }
                                call.respond(HttpStatusCode.OK, AssetResponse.fromAssetData(response.asset))
                            } ?: call.respond(HttpStatusCode.NotFound)
                            return@get
                        } else {
                            fetchAssetHandler
                                .fetchMetadataAtPath(requestContext)
                                .map {
                                    AssetResponse.fromAssetData(it)
                                }.let {
                                    call.respond(HttpStatusCode.OK, it)
                                }
                        }
                    }
                    ReturnFormat.REDIRECT -> {
                        fetchAssetHandler.fetchRedirectByPath(requestContext)?.let { response ->
                            if (response.url != null) {
                                call.response.headers.append(HttpHeaders.Location, response.url)
                                getAppStatusCacheHeader(response.cacheHit).let {
                                    call.response.headers.append(it.first, it.second)
                                }
                                call.respond(HttpStatusCode.TemporaryRedirect)
                            } else {
                                call.respondContent(
                                    objectStoreBucket = response.variant.objectStoreBucket,
                                    objectStoreKey = response.variant.objectStoreKey,
                                    cacheHit = response.cacheHit,
                                    alt = response.asset.alt,
                                    lqips = response.variant.lqips,
                                    entryId = response.asset.entryId,
                                    modifiedAt = response.asset.modifiedAt,
                                    mimeType = response.variant.transformation.format.mimeType,
                                    fetchAssetHandler = fetchAssetHandler,
                                )
                            }
                        } ?: call.respond(HttpStatusCode.NotFound)
                    }
                    ReturnFormat.LINK -> {
                        fetchAssetHandler.fetchLinkByPath(requestContext)?.let { response ->
                            getAppStatusCacheHeader(response.cacheHit).let {
                                call.response.headers.append(it.first, it.second)
                            }
                            call.respond(HttpStatusCode.OK, response.toResponse())
                        } ?: call.respond(HttpStatusCode.NotFound)
                    }
                    ReturnFormat.CONTENT, ReturnFormat.DOWNLOAD -> {
                        fetchAssetHandler.fetchMetadataByPath(requestContext, generateVariant = true)?.let { response ->
                            getContentDispositionHeader(
                                asset = response.asset,
                                returnFormat = requestContext.selectors.returnFormat,
                                imageFormat =
                                    response.asset.variants
                                        .first()
                                        .attributes.format,
                            )?.also {
                                call.response.headers.append(it.first, it.second)
                            }
                            val variant = response.asset.variants.first()
                            call.respondContent(
                                objectStoreBucket = variant.objectStoreBucket,
                                objectStoreKey = variant.objectStoreKey,
                                cacheHit = response.cacheHit,
                                alt = response.asset.alt,
                                lqips = variant.lqips,
                                entryId = response.asset.entryId,
                                modifiedAt = response.asset.modifiedAt,
                                mimeType = variant.transformation.format.mimeType,
                                fetchAssetHandler = fetchAssetHandler,
                            )
                        } ?: call.respond(HttpStatusCode.NotFound)
                    }
                }
            }

            post {
                call.storeNewAsset(assetUrlGenerator, storeNewAssetUseCase, maxMultipartContentLength)
            }

            post("/{...}") {
                call.storeNewAsset(assetUrlGenerator, storeNewAssetUseCase, maxMultipartContentLength)
            }

            put("/{...}") {
                val requestContext = call.attributes[updateRequestContextKey]
                logger.info("Received request to update asset ${requestContext.path}:${requestContext.entryId}")
                val asset =
                    updateAssetUseCase.updateAsset(
                        context = requestContext,
                        request = call.receive(StoreAssetRequest::class),
                    )
                call.respond(HttpStatusCode.OK, AssetResponse.fromAsset(asset.asset))
            }

            delete("/{...}") {
                deleteAssetUseCase.deleteAssets(call.attributes[deleteRequestContextKey])

                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private suspend fun RoutingCall.storeNewAsset(
    assetUrlGenerator: AssetUrlGenerator,
    storeNewAssetUseCase: StoreNewAssetUseCase,
    maxMultipartContentLength: Long,
) {
    when (request.contentType().withoutParameters()) {
        ContentType.MultiPart.FormData -> {
            logger.info("Received multipart request to store a new asset")
            storeMultipartAsset(
                storeNewAssetUseCase = storeNewAssetUseCase,
                maxMultipartContentLength = maxMultipartContentLength,
            )?.let { asset ->
                respondStoredAsset(assetUrlGenerator, asset)
            }
        }
        ContentType.Application.Json -> {
            logger.info("Received json request to store a new asset")
            val payload = receive(StoreAssetRequest::class)
            val asset =
                storeNewAssetUseCase.handleFromUrl(
                    request = payload,
                    uriPath = request.path(),
                )
            respondStoredAsset(assetUrlGenerator, asset)
        }
        else -> respond(HttpStatusCode.UnsupportedMediaType)
    }
}

private suspend fun RoutingCall.storeMultipartAsset(
    storeNewAssetUseCase: StoreNewAssetUseCase,
    maxMultipartContentLength: Long,
): AssetAndLocation? =
    coroutineScope {
        val assetData = CompletableDeferred<StoreAssetRequest>()
        val assetContentChannel = ByteChannel(true)
        var assetPartReceived = false
        var assetReceived = false
        var duplicateAssetReceived = false

        val deferredResponse =
            async {
                storeNewAssetUseCase.handleFromUpload(
                    deferredRequest = assetData,
                    multiPartContainer = AssetDataContainer(assetContentChannel, maxMultipartContentLength),
                    uriPath = request.path(),
                )
            }

        receiveMultipart().forEachPart { part ->
            when (part.name) {
                METADATA_PART_NAME -> part.readStoreAssetRequestInto(assetData)
                ASSET_PART_NAME -> {
                    if (assetPartReceived) {
                        duplicateAssetReceived = true
                        part.release()
                    } else {
                        assetPartReceived = true
                        assetReceived = part.copyAssetContentTo(assetContentChannel)
                    }
                }
                else -> part.release()
            }
        }

        when {
            duplicateAssetReceived -> {
                assetContentChannel.cancel(CancellationException("Duplicate asset payload"))
                deferredResponse.cancel()
                respond(HttpStatusCode.BadRequest, "Multiple asset payloads supplied")
                null
            }
            !assetData.isCompleted -> {
                assetContentChannel.cancel(CancellationException("Missing metadata"))
                deferredResponse.cancel()
                respond(HttpStatusCode.BadRequest, "No asset metadata supplied")
                null
            }
            !assetReceived -> {
                assetContentChannel.cancel(CancellationException("Missing asset payload"))
                deferredResponse.cancel()
                respond(HttpStatusCode.BadRequest, "No asset payload supplied")
                null
            }
            else -> deferredResponse.await()
        }
    }

private suspend fun PartData.readStoreAssetRequestInto(assetData: CompletableDeferred<StoreAssetRequest>) {
    try {
        if (this is PartData.FormItem) {
            assetData.complete(Json.decodeFromString(value))
        }
    } finally {
        release()
    }
}

private suspend fun RoutingCall.respondStoredAsset(
    assetUrlGenerator: AssetUrlGenerator,
    asset: AssetAndLocation,
) {
    logger.info("Created asset under path: ${asset.locationPath}")

    response.headers.append(
        name = HttpHeaders.Location,
        value =
            assetUrlGenerator.generateAbsoluteLocationUrl(
                path = asset.locationPath,
                entryId = checkNotNull(asset.asset.entryId),
            ),
    )
    respond(HttpStatusCode.Created, AssetResponse.fromAsset(asset.asset))
}
