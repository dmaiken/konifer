package asset

import asset.model.StoreAssetRequest
import io.asset.AssetStreamContainer
import io.asset.ContentParameters.ALL
import io.asset.ContentParameters.RETURN
import io.getEntryId
import io.getPathModifierOption
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
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject

private val logger = KtorSimpleLogger("asset")

const val ASSET_PATH_PREFIX = "/assets"

fun Application.configureAssetRouting() {
    val assetHandler by inject<AssetHandler>()

    routing {
        get("$ASSET_PATH_PREFIX/{...}") {
            val route = call.request.path().removePrefix(ASSET_PATH_PREFIX)
            val returnFormat = AssetReturnFormat.fromQueryParam(call.request.queryParameters[RETURN])
            val all = call.request.queryParameters[ALL]?.toBoolean() ?: false
            val suppliedEntryId = getEntryId(call.request)

            if (returnFormat == AssetReturnFormat.METADATA && !all) {
                logger.info("Navigating to asset info with path: $route")
                assetHandler.fetchAssetMetadataByPath(route, suppliedEntryId)?.let {
                    logger.info("Found asset info: $it with path: $route")
                    call.respond(HttpStatusCode.OK, it.toResponse())
                } ?: call.respond(HttpStatusCode.NotFound)
                return@get
            } else if (returnFormat == AssetReturnFormat.METADATA) {
                logger.info("Navigating to asset info of all assets with path: $route")
                assetHandler.fetchAssetInfoInPath(route).map {
                    it.toResponse()
                }.let {
                    logger.info("Found asset info for ${it.size} assets in path: $route")
                    call.respond(HttpStatusCode.OK, it)
                }
            } else if (returnFormat == AssetReturnFormat.REDIRECT) {
                logger.info("Navigating to asset with path: $route")
                assetHandler.fetchAssetByPath(route, suppliedEntryId, call.request.queryParameters)?.let { url ->
                    logger.info("Found asset with url: $url and route: $route")
                    call.response.headers.append(HttpHeaders.Location, url)
                    call.respond(HttpStatusCode.TemporaryRedirect)
                } ?: call.respond(HttpStatusCode.NotFound)
            } else {
                // Content
                logger.info("Navigating to asset content with path: $route")
                assetHandler.fetchAssetMetadataByPath(route, suppliedEntryId, call.request.queryParameters)?.let { asset ->
                    logger.info("Found asset content with path: $route")
                    call.respondBytesWriter(
                        contentType = ContentType.parse(asset.getOriginalVariant().attributes.mimeType),
                        status = HttpStatusCode.OK,
                    ) {
                        assetHandler.fetchAssetContent(
                            asset.getOriginalVariant().objectStoreBucket,
                            asset.getOriginalVariant().objectStoreKey,
                            this,
                        )
                    }
                } ?: call.respond(HttpStatusCode.NotFound)
            }
        }

        post(ASSET_PATH_PREFIX) {
            createNewAsset(call, assetHandler)
        }

        post("$ASSET_PATH_PREFIX/{...}") {
            createNewAsset(call, assetHandler)
        }

        delete("$ASSET_PATH_PREFIX/{...}") {
            val suppliedEntryId = getEntryId(call.request)
            val suppliedOption = getPathModifierOption(call.request)
            val route = call.request.path().removePrefix(ASSET_PATH_PREFIX)
            if (suppliedOption != null && suppliedEntryId != null) {
                throw IllegalArgumentException("Both entryId and option cannot both be supplied")
            }
            if (suppliedOption != null) {
                assetHandler.deleteAssets(route, suppliedOption)
            } else {
                assetHandler.deleteAsset(route, suppliedEntryId)
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }
}

suspend fun createNewAsset(
    call: RoutingCall,
    assetHandler: AssetHandler,
) {
    var assetData: StoreAssetRequest? = null
    var assetContent: ByteChannel? = null
    val multipart = call.receiveMultipart()
    multipart.forEachPart { part ->
        when (part) {
            is PartData.FormItem -> {
                if (part.name == "metadata") {
                    assetData = Json.decodeFromString(part.value)
                }
            }

            is PartData.FileItem -> {
                assetContent =
                    ByteChannel().also {
                        part.provider().copyTo(it)
                    }
                assetContent.close()
            }

            else -> {}
        }
        part.dispose()
    }
    if (assetData == null) {
        throw IllegalArgumentException("No asset metadata supplied")
    }
    if (assetContent == null) {
        throw IllegalArgumentException("No asset content supplied")
    }
    val asset =
        assetHandler.storeNewAsset(
            request = checkNotNull(assetData),
            container = AssetStreamContainer(checkNotNull(assetContent)),
            uriPath = call.request.path().removePrefix(ASSET_PATH_PREFIX),
        )
    logger.info("Created asset under path: ${asset.locationPath}")

    call.response.headers.append(HttpHeaders.Location, "http//${call.request.origin.localAddress}${asset.locationPath}")
    call.respond(HttpStatusCode.Created, asset.assetAndVariants.toResponse())
}
