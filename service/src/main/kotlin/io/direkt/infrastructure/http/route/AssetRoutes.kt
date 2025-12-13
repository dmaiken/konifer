package io.direkt.infrastructure.http.route

import io.direkt.domain.asset.AssetAndLocation
import io.direkt.domain.asset.AssetDataContainer
import io.direkt.domain.asset.DeleteMode
import io.direkt.domain.asset.MAX_BYTES_DEFAULT
import io.direkt.domain.workflows.DeleteAssetWorkflow
import io.direkt.domain.workflows.FetchAssetHandler
import io.direkt.domain.workflows.StoreNewAssetWorkflow
import io.direkt.domain.workflows.UpdateAssetWorkflow
import io.direkt.infrastructure.StoreAssetRequest
import io.direkt.infrastructure.http.AssetResponse
import io.direkt.infrastructure.http.AssetUrlGenerator
import io.direkt.infrastructure.http.getAppStatusCacheHeader
import io.direkt.infrastructure.http.getContentDispositionHeader
import io.direkt.infrastructure.properties.ConfigurationProperties.SOURCE
import io.direkt.infrastructure.properties.ConfigurationProperties.SourceConfigurationProperties.MULTIPART
import io.direkt.infrastructure.properties.ConfigurationProperties.SourceConfigurationProperties.MultipartConfigurationProperties.MAX_BYTES
import io.direkt.infrastructure.tryGetConfig
import io.direkt.service.context.RequestContextFactory
import io.direkt.service.context.ReturnFormat
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.Application
import io.ktor.server.config.tryGetString
import io.ktor.server.plugins.origin
import io.ktor.server.request.contentType
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.copyTo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject

private val logger = KtorSimpleLogger("io.direkt.infrastructure.http.AssetRouting")

const val ASSET_PATH_PREFIX = "/assets"

fun Application.configureAssetRouting() {
    val storeNewAssetWorkflow by inject<StoreNewAssetWorkflow>()
    val fetchAssetHandler by inject<FetchAssetHandler>()
    val deleteAssetWorkflow by inject<DeleteAssetWorkflow>()
    val updateAssetWorkflow by inject<UpdateAssetWorkflow>()
    val requestContextFactory by inject<RequestContextFactory>()
    val assetUrlGenerator by inject<AssetUrlGenerator>()
    val maxMultipartContentLength =
        environment.config
            .tryGetConfig(SOURCE)
            ?.tryGetConfig(MULTIPART)
            ?.tryGetString(MAX_BYTES)
            ?.toLong()
            ?: MAX_BYTES_DEFAULT

    routing {
        get("$ASSET_PATH_PREFIX/{...}") {
            val requestContext = requestContextFactory.fromGetRequest(call.request.path(), call.queryParameters)

            logger.info(
                "Navigating to asset (limit: ${requestContext.modifiers.limit}) with path (${requestContext.modifiers.returnFormat}): ${requestContext.path}",
            )
            when (requestContext.modifiers.returnFormat) {
                ReturnFormat.METADATA -> {
                    if (requestContext.modifiers.limit == 1) {
                        fetchAssetHandler.fetchAssetMetadataByPath(requestContext, generateVariant = false)?.let { response ->
                            logger.info("Found asset info: $response with path: ${requestContext.path}")
                            getAppStatusCacheHeader(response.cacheHit).let {
                                call.response.headers.append(it.first, it.second)
                            }
                            call.respond(HttpStatusCode.OK, AssetResponse.fromAssetData(response.asset))
                        } ?: call.respond(HttpStatusCode.NotFound)
                        return@get
                    } else {
                        fetchAssetHandler
                            .fetchAssetMetadataAtPath(requestContext)
                            .map {
                                AssetResponse.fromAssetData(it)
                            }.let {
                                logger.info("Found asset info for ${it.size} assets in path: ${requestContext.path}")
                                call.respond(HttpStatusCode.OK, it)
                            }
                    }
                }
                ReturnFormat.REDIRECT -> {
                    fetchAssetHandler.fetchAssetLinkByPath(requestContext)?.let { response ->
                        call.response.headers.append(HttpHeaders.Location, response.url)
                        getAppStatusCacheHeader(response.cacheHit).let {
                            call.response.headers.append(it.first, it.second)
                        }
                        call.respond(HttpStatusCode.TemporaryRedirect)
                    } ?: call.respond(HttpStatusCode.NotFound)
                }
                ReturnFormat.LINK -> {
                    fetchAssetHandler.fetchAssetLinkByPath(requestContext)?.let { response ->
                        getAppStatusCacheHeader(response.cacheHit).let {
                            call.response.headers.append(it.first, it.second)
                        }
                        call.respond(HttpStatusCode.OK, response.toResponse())
                    } ?: call.respond(HttpStatusCode.NotFound)
                }
                ReturnFormat.CONTENT, ReturnFormat.DOWNLOAD -> {
                    fetchAssetHandler.fetchAssetMetadataByPath(requestContext, generateVariant = true)?.let { response ->
                        logger.info("Found asset content with path: ${requestContext.path}")
                        getAppStatusCacheHeader(response.cacheHit).let {
                            call.response.headers.append(it.first, it.second)
                        }
                        getContentDispositionHeader(
                            asset = response.asset,
                            returnFormat = requestContext.modifiers.returnFormat,
                            imageFormat =
                                response.asset.variants
                                    .first()
                                    .attributes.format,
                        )?.also {
                            call.response.headers.append(it.first, it.second)
                        }
                        call.respondBytesWriter(
                            contentType =
                                ContentType.parse(
                                    response.asset.variants
                                        .first()
                                        .attributes.format.mimeType,
                                ),
                            status = HttpStatusCode.OK,
                        ) {
                            fetchAssetHandler.fetchAssetContent(
                                response.asset.variants
                                    .first()
                                    .objectStoreBucket,
                                response.asset.variants
                                    .first()
                                    .objectStoreKey,
                                this,
                            )
                        }
                    } ?: call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        post(ASSET_PATH_PREFIX) {
            storeNewAsset(assetUrlGenerator, call, storeNewAssetWorkflow, maxMultipartContentLength)
        }

        post("$ASSET_PATH_PREFIX/{...}") {
            storeNewAsset(assetUrlGenerator, call, storeNewAssetWorkflow, maxMultipartContentLength)
        }

        put("$ASSET_PATH_PREFIX/{...}") {
            val context = requestContextFactory.fromUpdateRequest(call.request.path())
            logger.info("Received request to update asset at path: ${context.path} and entryId: ${context.entryId}")
            val asset =
                updateAssetWorkflow.updateAsset(
                    context = context,
                    request = call.receive(StoreAssetRequest::class),
                )
            call.respond(HttpStatusCode.OK, AssetResponse.fromAsset(asset.asset))
        }

        delete("$ASSET_PATH_PREFIX/{...}") {
            val requestContext = requestContextFactory.fromDeleteRequest(call.request.path())
            logger.info("Deleting asset with path: ${requestContext.path}")
            if (requestContext.modifiers.mode != DeleteMode.SINGLE) {
                deleteAssetWorkflow.deleteAssets(requestContext.path, requestContext.modifiers.mode)
            } else {
                deleteAssetWorkflow.deleteAsset(requestContext.path, requestContext.modifiers.entryId)
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }
}

suspend fun storeNewAsset(
    assetUrlGenerator: AssetUrlGenerator,
    call: RoutingCall,
    storeNewAssetWorkflow: StoreNewAssetWorkflow,
    maxMultipartContentLength: Long,
) = coroutineScope {
    val deferredAsset = CompletableDeferred<AssetAndLocation>()
    when (call.request.contentType().withoutParameters()) {
        ContentType.MultiPart.FormData -> {
            logger.info("Received multipart request to store a new asset")
            val assetData = CompletableDeferred<StoreAssetRequest>()
            val assetContentChannel = ByteChannel(true)
            val deferredResponse =
                async {
                    storeNewAssetWorkflow.handleFromUpload(
                        deferredRequest = assetData,
                        multiPartContainer = AssetDataContainer(assetContentChannel, maxMultipartContentLength),
                        uriPath = call.request.path(),
                    )
                }
            val multipart = call.receiveMultipart()
            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        if (part.name == "metadata") {
                            assetData.complete(Json.decodeFromString(part.value))
                        }
                        part.dispose()
                    }

                    is PartData.FileItem -> {
                        try {
                            part.provider().copyTo(assetContentChannel)
                        } finally {
                            assetContentChannel.close()
                            part.dispose()
                        }
                    }

                    else -> part.dispose()
                }
            }
            if (!assetData.isCompleted) {
                throw IllegalArgumentException("No asset metadata supplied")
            }
            deferredAsset.complete(deferredResponse.await())
        }
        ContentType.Application.Json -> {
            logger.info("Received json request to store a new asset")
            val payload = call.receive(StoreAssetRequest::class)
            deferredAsset.complete(
                storeNewAssetWorkflow.handleFromUrl(
                    request = payload,
                    uriPath = call.request.path(),
                ),
            )
        }
    }
    val asset = deferredAsset.await()

    logger.info("Created asset under path: ${asset.locationPath}")

    call.response.headers.append(
        name = HttpHeaders.Location,
        value =
            assetUrlGenerator.generateEntryMetadataUrl(
                host = call.request.origin.localAddress,
                path = asset.locationPath,
                entryId = checkNotNull(asset.asset.entryId),
            ),
    )
    call.respond(HttpStatusCode.Created, AssetResponse.fromAsset(asset.asset))
}
