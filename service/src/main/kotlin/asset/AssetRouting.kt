package asset

import asset.model.StoreAssetRequest
import io.asset.AssetStreamContainer
import io.asset.context.RequestContextFactory
import io.asset.context.ReturnFormat
import io.getAppStatusCacheHeader
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.Application
import io.ktor.server.plugins.origin
import io.ktor.server.request.path
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

private val logger = KtorSimpleLogger("asset")

const val ASSET_PATH_PREFIX = "/assets"

fun Application.configureAssetRouting() {
    val assetHandler by inject<AssetHandler>()
    val requestContextFactory by inject<RequestContextFactory>()

    routing {
        get("$ASSET_PATH_PREFIX/{...}") {
            val requestContext = requestContextFactory.fromGetRequest(call.request.path(), call.queryParameters)

            when (requestContext.modifiers.returnFormat) {
                ReturnFormat.METADATA -> {
                    if (requestContext.modifiers.limit == 1) {
                        logger.info("Navigating to asset info with path: ${requestContext.path}")
                        assetHandler.fetchAssetMetadataByPath(requestContext, generateVariant = false)?.let {
                            logger.info("Found asset info: $it with path: ${requestContext.path}")
                            call.respond(HttpStatusCode.OK, it.first.toResponse())
                        } ?: call.respond(HttpStatusCode.NotFound)
                        return@get
                    } else {
                        logger.info("Navigating to asset info of all assets with path: ${requestContext.path}")
                        assetHandler.fetchAssetMetadataInPath(requestContext).map {
                            it.toResponse()
                        }.let {
                            logger.info("Found asset info for ${it.size} assets in path: ${requestContext.path}")
                            call.respond(HttpStatusCode.OK, it)
                        }
                    }
                }
                ReturnFormat.REDIRECT -> {
                    logger.info("Navigating to asset with path (${ReturnFormat.REDIRECT}): ${requestContext.path}")
                    assetHandler.fetchAssetLinksByPath(requestContext)?.let { response ->
                        call.response.headers.append(HttpHeaders.Location, response.url)
                        getAppStatusCacheHeader(response.cacheHit).let {
                            call.response.headers.append(it.first, it.second)
                        }
                        call.respond(HttpStatusCode.TemporaryRedirect)
                    } ?: call.respond(HttpStatusCode.NotFound)
                }
                ReturnFormat.LINK -> {
                    logger.info("Navigating to asset with path (${ReturnFormat.LINK}: ${requestContext.path}")
                    assetHandler.fetchAssetLinksByPath(requestContext)?.let { response ->
                        getAppStatusCacheHeader(response.cacheHit).let {
                            call.response.headers.append(it.first, it.second)
                        }
                        call.respond(HttpStatusCode.OK, response.toResponse())
                    } ?: call.respond(HttpStatusCode.NotFound)
                }
                ReturnFormat.CONTENT -> {
                    logger.info("Navigating to asset content with path: ${requestContext.path}")
                    assetHandler.fetchAssetMetadataByPath(requestContext, generateVariant = true)?.let { response ->
                        logger.info("Found asset content with path: ${requestContext.path}")
                        getAppStatusCacheHeader(response.second).let {
                            call.response.headers.append(it.first, it.second)
                        }
                        call.respondBytesWriter(
                            contentType = ContentType.parse(response.first.variants.first().attributes.mimeType),
                            status = HttpStatusCode.OK,
                        ) {
                            assetHandler.fetchAssetContent(
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
            createNewAsset(call, assetHandler)
        }

        post("$ASSET_PATH_PREFIX/{...}") {
            createNewAsset(call, assetHandler)
        }

        delete("$ASSET_PATH_PREFIX/{...}") {
            val requestContext = requestContextFactory.fromDeleteRequest(call.request.path())
            logger.info("Deleting asset with path: ${requestContext.path}")
            if (requestContext.modifiers.mode != DeleteMode.SINGLE) {
                assetHandler.deleteAssets(requestContext.path, requestContext.modifiers.mode)
            } else {
                assetHandler.deleteAsset(requestContext.path, requestContext.modifiers.entryId)
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }
}

suspend fun createNewAsset(
    call: RoutingCall,
    assetHandler: AssetHandler,
) = coroutineScope {
    logger.info("Received request to store a new asset")
    val assetData = CompletableDeferred<StoreAssetRequest>()
    val assetContent = ByteChannel(true)
    val multipart = call.receiveMultipart()

    val deferredAsset =
        async {
            assetHandler.storeNewAsset(
                deferredRequest = assetData,
                container = AssetStreamContainer(assetContent),
                uriPath = call.request.path().removePrefix(ASSET_PATH_PREFIX),
            )
        }

    multipart.forEachPart { part ->
        when (part) {
            is PartData.FormItem -> {
                if (part.name == "metadata") {
                    assetData.complete(Json.decodeFromString(part.value))
                }
            }

            is PartData.FileItem -> {
                try {
                    part.provider().copyTo(assetContent)
                } finally {
                    assetContent.close()
                }
            }

            else -> {}
        }
        part.dispose()
    }
    if (!assetData.isCompleted) {
        throw IllegalArgumentException("No asset metadata supplied")
    }
    val asset = deferredAsset.await()

    logger.info("Created asset under path: ${asset.locationPath}")

    call.response.headers.append(HttpHeaders.Location, "http//${call.request.origin.localAddress}${asset.locationPath}")
    call.respond(HttpStatusCode.Created, asset.assetAndVariants.toResponse())
}
