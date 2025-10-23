package io.asset

import io.asset.context.RequestContextFactory
import io.asset.context.ReturnFormat
import io.asset.handler.AssetAndLocation
import io.asset.handler.DeleteAssetHandler
import io.asset.handler.FetchAssetHandler
import io.asset.handler.StoreAssetHandler
import io.asset.model.StoreAssetRequest
import io.getAppStatusCacheHeader
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
import io.ktor.server.routing.routing
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.copyTo
import io.path.DeleteMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject

private val logger = KtorSimpleLogger("io.asset")

const val ASSET_PATH_PREFIX = "/assets"

fun Application.configureAssetRouting() {
    val storeAssetHandler by inject<StoreAssetHandler>()
    val fetchAssetHandler by inject<FetchAssetHandler>()
    val deleteAssetHandler by inject<DeleteAssetHandler>()
    val requestContextFactory by inject<RequestContextFactory>()
    val maxMultipartContentLength = environment.config.tryGetString("source.multipart.max-bytes")?.toLong() ?: MAX_BYTES_DEFAULT

    routing {
        get("$ASSET_PATH_PREFIX/{...}") {
            val requestContext = requestContextFactory.fromGetRequest(call.request.path(), call.queryParameters)

            when (requestContext.modifiers.returnFormat) {
                ReturnFormat.METADATA -> {
                    if (requestContext.modifiers.limit == 1) {
                        logger.info("Navigating to asset info with path: ${requestContext.path}")
                        fetchAssetHandler.fetchAssetMetadataByPath(requestContext, generateVariant = false)?.let {
                            logger.info("Found asset info: $it with path: ${requestContext.path}")
                            call.respond(HttpStatusCode.OK, it.first.toResponse())
                        } ?: call.respond(HttpStatusCode.NotFound)
                        return@get
                    } else {
                        logger.info("Navigating to asset info of all assets with path: ${requestContext.path}")
                        fetchAssetHandler.fetchAssetMetadataInPath(requestContext).map {
                            it.toResponse()
                        }.let {
                            logger.info("Found asset info for ${it.size} assets in path: ${requestContext.path}")
                            call.respond(HttpStatusCode.OK, it)
                        }
                    }
                }
                ReturnFormat.REDIRECT -> {
                    logger.info("Navigating to asset with path (${ReturnFormat.REDIRECT}): ${requestContext.path}")
                    fetchAssetHandler.fetchAssetLinkByPath(requestContext)?.let { response ->
                        call.response.headers.append(HttpHeaders.Location, response.url)
                        getAppStatusCacheHeader(response.cacheHit).let {
                            call.response.headers.append(it.first, it.second)
                        }
                        call.respond(HttpStatusCode.TemporaryRedirect)
                    } ?: call.respond(HttpStatusCode.NotFound)
                }
                ReturnFormat.LINK -> {
                    logger.info("Navigating to asset with path (${ReturnFormat.LINK}: ${requestContext.path}")
                    fetchAssetHandler.fetchAssetLinkByPath(requestContext)?.let { response ->
                        getAppStatusCacheHeader(response.cacheHit).let {
                            call.response.headers.append(it.first, it.second)
                        }
                        call.respond(HttpStatusCode.OK, response.toResponse())
                    } ?: call.respond(HttpStatusCode.NotFound)
                }
                ReturnFormat.CONTENT -> {
                    logger.info("Navigating to asset content with path: ${requestContext.path}")
                    fetchAssetHandler.fetchAssetMetadataByPath(requestContext, generateVariant = true)?.let { response ->
                        logger.info("Found asset content with path: ${requestContext.path}")
                        getAppStatusCacheHeader(response.second).let {
                            call.response.headers.append(it.first, it.second)
                        }
                        call.respondBytesWriter(
                            contentType = ContentType.parse(response.first.variants.first().transformation.format.mimeType),
                            status = HttpStatusCode.OK,
                        ) {
                            fetchAssetHandler.fetchAssetContent(
                                response.first.variants.first().objectStoreBucket,
                                response.first.variants.first().objectStoreKey,
                                this,
                            )
                        }
                    } ?: call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        post(ASSET_PATH_PREFIX) {
            storeNewAsset(call, storeAssetHandler, maxMultipartContentLength)
        }

        post("$ASSET_PATH_PREFIX/{...}") {
            storeNewAsset(call, storeAssetHandler, maxMultipartContentLength)
        }

        delete("$ASSET_PATH_PREFIX/{...}") {
            val requestContext = requestContextFactory.fromDeleteRequest(call.request.path())
            logger.info("Deleting asset with path: ${requestContext.path}")
            if (requestContext.modifiers.mode != DeleteMode.SINGLE) {
                deleteAssetHandler.deleteAssets(requestContext.path, requestContext.modifiers.mode)
            } else {
                deleteAssetHandler.deleteAsset(requestContext.path, requestContext.modifiers.entryId)
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }
}

suspend fun storeNewAsset(
    call: RoutingCall,
    storeAssetHandler: StoreAssetHandler,
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
                    storeAssetHandler.storeNewAssetFromUpload(
                        deferredRequest = assetData,
                        multiPartContainer = AssetStreamContainer(assetContentChannel, maxMultipartContentLength),
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
            val payload = call.receive(StoreAssetRequest::class)
            deferredAsset.complete(
                storeAssetHandler.storeNewAssetFromUrl(
                    request = payload,
                    uriPath = call.request.path(),
                ),
            )
        }
    }
    val asset = deferredAsset.await()

    logger.info("Created asset under path: ${asset.locationPath}")

    call.response.headers.append(HttpHeaders.Location, "http//${call.request.origin.localAddress}${asset.locationPath}")
    call.respond(HttpStatusCode.Created, asset.assetAndVariants.toResponse())
}
