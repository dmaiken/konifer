package io.konifer.entrypoint

import io.konifer.common.http.AssetContentHeaders
import io.konifer.common.http.AssetEntriesResponse
import io.konifer.common.http.AssetLinkResponse
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.AssetSourceRequest
import io.konifer.common.http.ErrorResponse
import io.konifer.common.http.HttpSource
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.Filter
import io.konifer.common.image.Fit
import io.konifer.common.image.Flip
import io.konifer.common.image.Gravity
import io.konifer.common.image.ImageFormat
import io.konifer.common.image.LIMIT_PARAMETER
import io.konifer.common.image.ManipulationParameters
import io.konifer.common.image.Rotate
import io.konifer.common.image.TransformableColorSpace
import io.konifer.common.image.pixel.PixelLayout
import io.konifer.infrastructure.http.APP_ALT
import io.konifer.infrastructure.http.APP_CACHE_STATUS
import io.konifer.infrastructure.http.APP_LQIP_BLURHASH
import io.konifer.infrastructure.http.APP_LQIP_THUMBHASH
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.Encoding
import io.ktor.openapi.ExampleObject
import io.ktor.openapi.GenericElement
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonSchemaInference
import io.ktor.openapi.JsonType
import io.ktor.openapi.Operation
import io.ktor.openapi.Parameters
import io.ktor.openapi.ReferenceOr.Companion.value
import io.ktor.openapi.Response
import io.ktor.openapi.Responses
import io.ktor.openapi.jsonSchema

internal fun Operation.Builder.fetchAssetDocumentation() {
    operationId = "fetchAsset"
    tag("Assets")
    summary = "Fetch an asset, its metadata, or its content"
    description =
        """
        The path can span multiple segments. Append selectors after `/-/`: `new` (default) or `modified`,
        followed by `link` (default), `redirect`, `info`, `entries`, `content`, or `download`.
        Only a complete `-` path segment introduces selectors. Ordinary segments may contain or end with hyphens.
        Empty selector sections (`/-` or `/-/`) and multiple separator segments are invalid.
        Select a specific entry with `/-/entry/{entryId}` and an optional return format.
        Examples: `/assets/users/123/profile-picture/-/info`, `/assets/users/123/profile-picture/-/entry/42/content`.

        `link` returns the resolved delivery URL and selected variant attributes; `redirect` returns a 307 to that URL.
        `info` returns one asset's metadata; `entries` returns a list (empty when nothing matches).
        `content` streams bytes; `download` also sets Content-Disposition. Transformations and profiles are not
        permitted for `info` or `entries`. Path configuration controls which transformations are allowed.
        Service delivery URLs pin the selected entry and transformation, including negotiated format, and are signed
        when URL signing is enabled. They can be fetched without the original Accept header or a signing client.
        Query parameters override profile values when permitted. In profile_only mode, transformed content requires
        a profile and explicit parameters must normalize to that profile; profiles without a format allow format negotiation.
        Output format is resolved from `format`, then the profile,
        then a recognized image MIME type in Accept; otherwise the original format is retained.
        Unknown query parameters are ignored. Only parameters prefixed with `label:` filter labels.

        `format=pixels` returns unsigned 8-bit raw pixel bytes as application/octet-stream, without an image header.
        Pixels are ordered by rows; channel order and memory layout are controlled by `pixel-channels` and
        `pixel-layout`. Only the first frame of an animated image is returned. Content/download responses include
        selected variant attributes in K-Attr-* headers, including dimensions and pixel channels, layout, and data type.
        The same attributes are available through `link`. Rotate is the clockwise angle still needed for display,
        followed by horizontal flip; already normalized pixels report 0 and false. Optional headers are omitted.
        Redirect delivery does not include these attributes on the object storage response.
        Raw pixels are output-only and cannot be uploaded.
        """.trimIndent()
    parameters {
        assetPath("users/123/profile-picture/-/content")
        query(LIMIT_PARAMETER) {
            description = "Maximum entries returned by /-/entries: default 20, -1 for all. Other formats allow only 1. Supply once."
            schema = limitSchema()
        }
        labelFilters()
        transformationParameters()
        header(HttpHeaders.Accept) {
            description = "An image MIME type may select output format when neither format nor the profile selects it."
            schema = JsonSchema(type = JsonType.STRING)
        }
        header(HttpHeaders.IfNoneMatch) {
            description = "Return 304 for unchanged content/download responses matching this ETag."
            schema = JsonSchema(type = JsonType.STRING)
        }
        header(HttpHeaders.IfModifiedSince) {
            description = "Return 304 for content/download responses unchanged since this HTTP date."
            schema = JsonSchema(type = JsonType.STRING)
        }
        query("s") {
            description = "URL signature; required for asset GET requests when URL signing is enabled."
            schema = JsonSchema(type = JsonType.STRING)
        }
    }
    responses { fetchAssetResponses() }
}

internal fun Operation.Builder.storeAssetDocumentation(atRoot: Boolean) {
    operationId = if (atRoot) "storeAssetAtRoot" else "storeAsset"
    tag("Assets")
    summary = if (atRoot) "Store an asset at the root path" else "Store an asset at a path"
    description =
        """
        Store image content and metadata using exactly one source: multipart upload, HTTP URL, or Amazon S3 object ARN.
        Multipart requests require one `asset` file and one `metadata` JSON part (which may be `{}`). Do not include
        external source fields in multipart metadata. JSON requests require `source.http.url` or `source.s3.arn`;
        top-level `url` is a deprecated fallback. HTTP hosts must be allowed by source configuration; S3 imports
        require source AWS credentials. Remote downloads are subject to the configured byte limit.
        `alt`, `labels`, and `tags` are optional. The path cannot contain `/-/` selectors.
        The response contains the stored asset's metadata and a Location header identifying its entry.
        Raw `pixels` are an output format and cannot be used as an upload source.
        """.trimIndent()
    if (!atRoot) parameters { assetPath("users/123/profile-picture") }
    requestBody {
        required = true
        ContentType.Application.Json {
            schema = jsonSchema<StoreAssetRequest>()
            example(
                "httpSource",
                ExampleObject(
                    value =
                        GenericElement(
                            StoreAssetRequest(
                                source =
                                    AssetSourceRequest(
                                        http = HttpSource("https://konifer.io/img/konifer-small.png"),
                                    ),
                                alt = "Konifer logo",
                            ),
                        ),
                ),
            )
        }
        ContentType.MultiPart.FormData {
            schema =
                JsonSchema(
                    type = JsonType.OBJECT,
                    required = listOf("metadata", "asset"),
                    properties =
                        mapOf(
                            "metadata" to value(assetMetadataSchema()),
                            "asset" to value(JsonSchema(type = JsonType.STRING, format = "binary")),
                        ),
                )
            encoding("metadata", Encoding(contentType = ContentType.Application.Json))
            encoding("asset", Encoding(contentType = ContentType.Image.Any))
        }
    }
    responses { storeAssetResponses() }
}

internal fun Operation.Builder.updateAssetDocumentation() {
    operationId = "updateAsset"
    tag("Assets")
    summary = "Replace an asset's metadata"
    description =
        """
        Specify the entry in the path: `/assets/users/123/profile-picture/-/entry/42`.
        Replace all editable metadata (`alt`, `labels`, and `tags`). Omitted fields are cleared; this is not a patch.
        Asset content and source cannot be updated. Ordering and return-format selectors are not permitted.
        """.trimIndent()
    parameters { assetEntryPath("users/123/profile-picture/-/entry/42") }
    requestBody {
        required = true
        ContentType.Application.Json {
            schema = assetMetadataSchema()
        }
    }
    responses {
        HttpStatusCode.OK {
            description = "The asset with its updated metadata."
            ContentType.Application.Json { schema = jsonSchema<AssetResponse>() }
        }
        errorResponse(HttpStatusCode.InternalServerError, "Unexpected server failure.")
        errorResponse(HttpStatusCode.BadRequest, "Invalid path, metadata, selectors, or asset state.")
        errorResponse(HttpStatusCode.NotFound, "The specified entry does not exist.")
        errorResponse(HttpStatusCode.PayloadTooLarge, "Request body exceeds the configured limit.")
        errorResponse(HttpStatusCode.UnsupportedMediaType, "Metadata must be sent as application/json.")
    }
}

internal fun Operation.Builder.deleteAssetDocumentation() {
    operationId = "deleteAssets"
    tag("Assets")
    summary = "Delete selected assets"
    description =
        """
        Select a specific entry with `/-/entry/{entryId}`, or use `/-/recursive` to delete all entries at the path
        and its descendants. `limit=-1` without selectors deletes all entries at the exact path without deleting descendants.
        A selection is required. Ordering selectors and bounded deletion are not supported; list entries and delete
        explicit entry IDs to delete a bounded selection. Entry and recursive selectors cannot be combined with limit.
        Label filters apply to exact-path and recursive bulk deletion. Bulk operations evaluate matches when executed;
        they do not preserve a snapshot across concurrent uploads. Repeated deletion succeeds when no entries remain.
        Only `limit` and parameters prefixed with `label:` are accepted; unknown query parameters return 400.
        Asset metadata and variant references are deleted synchronously; object-store content is deleted
        asynchronously. Deletion is irreversible. Return 204 even when no entries match.
        """.trimIndent()
    parameters {
        assetPath("users/123/profile-picture/-/entry/42")
        query(LIMIT_PARAMETER) {
            description =
                "Must be -1 to explicitly delete all entries at the exact path. Supply once; omit with entry or recursive selectors."
            schema = JsonSchema(type = JsonType.INTEGER, enum = listOf(GenericElement(-1)))
        }
        labelFilters()
    }
    responses {
        HttpStatusCode.NoContent { description = "Deletion completed, including when no entries matched. No response body." }
        errorResponse(HttpStatusCode.InternalServerError, "Unexpected server failure.")
        errorResponse(HttpStatusCode.BadRequest, "Invalid path, selectors, labels, limit combination, or unknown query parameters.")
    }
}

private fun JsonSchemaInference.assetMetadataSchema(): JsonSchema =
    jsonSchema<StoreAssetRequest>().let {
        it.copy(
            title = "AssetMetadata",
            properties = it.properties?.filterKeys { name -> name in setOf("alt", "labels", "tags") },
        )
    }

private fun Parameters.Builder.assetPath(examplePath: String) {
    // Ktor renders tailcard selectors as {**}, including unnamed ones. Match that generated template.
    path("**") {
        description = "Asset path relative to /assets, including any /-/ selectors. May span multiple URL segments."
        schema = JsonSchema(type = JsonType.STRING)
        example = GenericElement(examplePath)
    }
}

private fun Parameters.Builder.assetEntryPath(examplePath: String) {
    // Ktor renders tailcard selectors as {**}, including unnamed ones. Match that generated template.
    path("**") {
        description = "Asset path relative to /assets, including the entry ID selector."
        schema = JsonSchema(type = JsonType.STRING)
        example = GenericElement(examplePath)
    }
}

private fun Parameters.Builder.labelFilters() {
    query("labels") {
        description =
            "Label filters encoded as independent query parameters, e.g. label:purpose=profile. " +
            "Only parameters prefixed with label: filter labels. All supplied labels must match."
        style = "form"
        explode = true
        schema = jsonSchema<Map<String, String>>()
        example = GenericElement(mapOf("label:purpose" to "profile"))
    }
}

private fun Parameters.Builder.transformationParameters() {
    query(ManipulationParameters.VARIANT_PROFILE) {
        description = "Named variant profile. Explicit transformation parameters override its values when permitted."
        schema = JsonSchema(type = JsonType.STRING)
    }
    query(ManipulationParameters.WIDTH) {
        description = "Target width in pixels. With fit=fit, omitted height preserves aspect ratio. Subject to path limits."
        schema = JsonSchema(type = JsonType.INTEGER, minimum = 1.0)
    }
    query(ManipulationParameters.HEIGHT) {
        description = "Target height in pixels. With fit=fit, omitted width preserves aspect ratio. Subject to path limits."
        schema = JsonSchema(type = JsonType.INTEGER, minimum = 1.0)
    }
    query(ManipulationParameters.FORMAT) {
        description = "Output image format. pixels is output-only raw unsigned 8-bit data; use format=pixels explicitly."
        schema = jsonSchema<ImageFormat>()
    }
    query(ManipulationParameters.FIT) {
        description = "Resize mode (default fit). fill, stretch, and crop require both w and h."
        schema = jsonSchema<Fit>()
    }
    query(ManipulationParameters.GRAVITY) {
        description = "Crop gravity (default center): geometric center, entropy, or attention/saliency."
        schema = jsonSchema<Gravity>()
    }
    query(ManipulationParameters.ROTATE) {
        description = "Clockwise rotation in degrees; auto uses EXIF orientation. Default 0."
        schema = jsonSchema<Rotate>()
    }
    query(ManipulationParameters.FLIP) {
        description = "Mirror horizontally (h), vertically (v), or neither (none, default)."
        schema = jsonSchema<Flip>()
    }
    query(ManipulationParameters.FILTER) {
        description = "Visual filter (default none). grayscale retains RGB channels; color-space=grayscale changes color space."
        schema = jsonSchema<Filter>()
    }
    effectParameters()
    pixelParameters()
}

private fun Parameters.Builder.effectParameters() {
    query(ManipulationParameters.BLUR) {
        description = "Gaussian blur sigma, from 0 (none) to 150."
        schema = JsonSchema(type = JsonType.INTEGER, minimum = 0.0, maximum = 150.0)
    }
    query(ManipulationParameters.QUALITY) {
        description = "Lossy compression quality, 1 to 100. Default depends on format; ignored for PNG and pixels."
        schema = JsonSchema(type = JsonType.INTEGER, minimum = 1.0, maximum = 100.0)
    }
    query(ManipulationParameters.PAD) {
        description = "Padding amount in pixels (default 0)."
        schema = JsonSchema(type = JsonType.INTEGER, minimum = 0.0)
    }
    query(ManipulationParameters.PAD_COLOR) {
        description = "Padding color as #RRGGBB or #RRGGBBAA. URL-encode # as %23. Default is format-dependent."
        schema = JsonSchema(type = JsonType.STRING)
    }
    query(ManipulationParameters.STRIP) {
        description = "Comma-separated metadata categories to remove: exif, xmp, iptc (e.g. exif,xmp)."
        schema = JsonSchema(type = JsonType.STRING)
    }
    query(ManipulationParameters.COLOR_SPACE) {
        description = "Output color space (default origin). RGB pixel channels require srgb or p3."
        schema = jsonSchema<TransformableColorSpace>()
    }
}

private fun Parameters.Builder.pixelParameters() {
    query(ManipulationParameters.PIXEL_CHANNELS) {
        description =
            "For format=pixels: any permutation of rgb, or rgba, or gray (all case-insensitive). " +
            "Each color channel must occur exactly once. Default RGB. Examples: RGB, BGR, RGBA, BGRA, ARGB. " +
            "gray produces one grayscale channel and requires color-space=origin or color-space=grayscale when color-space is supplied. " +
            "For gray, transparency is composited against white. RGBA channels add opaque alpha when the source lacks it."
        schema = JsonSchema(type = JsonType.STRING, minLength = 3, maxLength = 4)
    }
    query(ManipulationParameters.PIXEL_LAYOUT) {
        description =
            "For format=pixels: interleaved (default, RGBRGB...) or planar (RR...GG...BB...). " +
            "Channel order follows pixel-channels. gray always normalizes to interleaved."
        schema = jsonSchema<PixelLayout>()
    }
}

private fun Responses.Builder.fetchAssetResponses() {
    HttpStatusCode.OK {
        description = "Selected representation: JSON for link/info/entries, binary bytes for content/download."
        ContentType.Application.Json {
            schema =
                JsonSchema(
                    oneOf =
                        listOf(
                            value(jsonSchema<AssetLinkResponse>()),
                            value(jsonSchema<AssetResponse>()),
                            value(jsonSchema<AssetEntriesResponse>()),
                        ),
                )
        }
        ImageFormat.entries.forEach { format ->
            val contentType = ContentType.parse(format.mimeType)
            contentType {
                schema = JsonSchema(type = JsonType.STRING, format = "binary")
            }
        }
        assetContentHeaders()
    }
    HttpStatusCode.TemporaryRedirect {
        description = "redirect selector: resolved delivery URL, using the configured delivery strategy."
        headers {
            header(HttpHeaders.Location) {
                required = true
                description = "The selected variant's delivery URL."
                schema = JsonSchema(type = JsonType.STRING, format = "uri")
            }
            header(APP_CACHE_STATUS) { schema = cacheStatusSchema() }
        }
    }
    HttpStatusCode.NotModified { description = "Content unchanged according to conditional request headers. No response body." }
    errorResponse(HttpStatusCode.InternalServerError, "Unexpected server failure.")
    errorResponse(HttpStatusCode.BadRequest, "Invalid selectors, parameters, profile, or disallowed transformation.")
    errorResponse(HttpStatusCode.Forbidden, "Missing or invalid signature when URL signing is enabled.")
    errorResponse(HttpStatusCode.NotFound, "No asset matches the selection (except entries, which returns an empty list).")
}

private fun Response.Builder.assetContentHeaders() {
    headers {
        header(APP_CACHE_STATUS) {
            description = "hit or miss for the selected representation; not returned for entries."
            schema = cacheStatusSchema()
        }
        listOf(APP_ALT, APP_LQIP_BLURHASH, APP_LQIP_THUMBHASH).forEach { name ->
            header(name) {
                description = "Optional asset metadata on content/download responses, when configured or supplied."
                schema = JsonSchema(type = JsonType.STRING)
            }
        }
        AssetContentHeaders.attributeNames.forEach { name ->
            header(name) {
                description = "Selected variant attribute on content/download responses; omitted when inapplicable."
                schema =
                    when (name) {
                        AssetContentHeaders.WIDTH, AssetContentHeaders.HEIGHT, AssetContentHeaders.PAGE_COUNT, AssetContentHeaders.LOOP -> {
                            JsonSchema(type = JsonType.INTEGER)
                        }

                        AssetContentHeaders.HORIZONTAL_FLIP -> {
                            JsonSchema(type = JsonType.BOOLEAN)
                        }

                        AssetContentHeaders.ROTATE -> {
                            JsonSchema(type = JsonType.STRING, enum = listOf("0", "90", "180", "270").map { GenericElement(it) })
                        }

                        else -> {
                            JsonSchema(type = JsonType.STRING)
                        }
                    }
            }
        }
        listOf(HttpHeaders.ETag, HttpHeaders.LastModified, HttpHeaders.CacheControl).forEach { name ->
            header(name) {
                description = "Caching header on applicable responses, subject to path configuration."
                schema = JsonSchema(type = JsonType.STRING)
            }
        }
        header(HttpHeaders.ContentDisposition) {
            description = "download selector: attachment filename derived from alt or the asset path."
            schema = JsonSchema(type = JsonType.STRING)
        }
    }
}

private fun Responses.Builder.storeAssetResponses() {
    HttpStatusCode.Created {
        description = "Asset stored successfully."
        ContentType.Application.Json { schema = jsonSchema<AssetResponse>() }
        headers {
            header(HttpHeaders.Location) {
                required = true
                description = "Absolute entry-specific asset URL, using http.public-url when configured."
                schema = JsonSchema(type = JsonType.STRING, format = "uri")
            }
        }
    }
    errorResponse(HttpStatusCode.InternalServerError, "Unexpected server failure.")
    errorResponse(HttpStatusCode.BadRequest, "Invalid metadata, image, source combination, or upload rejected by a rule.")
    errorResponse(HttpStatusCode.Forbidden, "Image content type or remote source is not permitted.")
    errorResponse(HttpStatusCode.PayloadTooLarge, "Request body exceeds the configured limit.")
    errorResponse(HttpStatusCode.UnsupportedMediaType, "Use application/json or multipart/form-data.")
    errorResponse(HttpStatusCode.UnprocessableEntity, "Invalid or oversized external source.")
    errorResponse(HttpStatusCode.BadGateway, "External source is unavailable.")
    errorResponse(HttpStatusCode.GatewayTimeout, "Retrieving the external source timed out.")
}

private fun Responses.Builder.errorResponse(
    status: HttpStatusCode,
    message: String,
) {
    status {
        description = message
        ContentType.Application.Json {
            schema = jsonSchema<ErrorResponse>()
        }
    }
}

private fun cacheStatusSchema() = JsonSchema(type = JsonType.STRING, enum = listOf(GenericElement("hit"), GenericElement("miss")))

private fun limitSchema() =
    JsonSchema(
        oneOf =
            listOf(
                value(JsonSchema(type = JsonType.INTEGER, minimum = 1.0, maximum = Int.MAX_VALUE.toDouble())),
                value(JsonSchema(type = JsonType.INTEGER, enum = listOf(GenericElement(-1)))),
            ),
    )
