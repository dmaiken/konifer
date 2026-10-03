package io.konifer

import io.konifer.clientV2.HmacSigningAlgorithm
import io.konifer.clientV2.KoniferClientV2
import io.konifer.clientV2.KoniferInternalTestApi
import io.konifer.clientV2.KoniferV2Result
import io.konifer.clientV2.assets.AssetSelection
import io.konifer.clientV2.assets.fetch.ContentDelivery
import io.konifer.clientV2.assets.fetch.DeleteQuerySelector
import io.konifer.clientV2.assets.fetch.EntryId
import io.konifer.clientV2.assets.fetch.FetchQuerySelector
import io.konifer.clientV2.assets.fetch.None
import io.konifer.clientV2.assets.fetch.OrderBy
import io.konifer.clientV2.assets.fetch.Recursive
import io.konifer.clientV2.assets.fetch.RequestedTransformation
import io.konifer.clientV2.assets.store.NewAssetAtPath
import io.konifer.common.http.AssetLinkResponse
import io.konifer.common.http.AssetResponse
import io.konifer.common.http.EvaluateRuleDefinitionsRequest
import io.konifer.common.http.EvaluateRuleDefinitionsResponse
import io.konifer.common.http.StoreAssetRequest
import io.konifer.common.image.ImageFormat
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ClientProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

class KoniferTestScope(
    private val clientProvider: ClientProvider,
    coroutineScope: CoroutineScope,
) : CoroutineScope by coroutineScope {
    private var currentClient = createClient()
    private var hmacKey: String? = null
    private var hmacSigningAlgorithm = HmacSigningAlgorithm.HMAC_SHA256

    val client: HttpClient get() = currentClient

    // Backing field to cache the initialized client
    private var cachedKoniferClient: KoniferClientV2? = null

    @OptIn(KoniferInternalTestApi::class)
    suspend fun konifer(): KoniferClientV2 =
        cachedKoniferClient ?: KoniferClientV2
            .buildForTesting(
                testClient = client,
                hmacKey = hmacKey,
                hmacSigningAlgorithm = hmacSigningAlgorithm,
            ).also { cachedKoniferClient = it }

    fun configureKoniferHmacSigning(
        hmacKey: String?,
        hmacSigningAlgorithm: HmacSigningAlgorithm = HmacSigningAlgorithm.HMAC_SHA256,
    ) {
        this.hmacKey = hmacKey
        this.hmacSigningAlgorithm = hmacSigningAlgorithm
        cachedKoniferClient = null
    }

    /**
     * Configure the test [HttpClient]. JSON content negotiation is already enabled.
     */
    fun configureClient(block: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit): HttpClient {
        currentClient.close()
        currentClient = createClient(block)
        return currentClient
    }

    private fun createClient(block: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit = {}) =
        clientProvider.createClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        explicitNulls = false
                    },
                )
            }
            block()
        }

    suspend fun KoniferClientV2.storeAsset(
        path: String,
        format: ImageFormat,
        request: StoreAssetRequest,
        bytes: ByteArray,
    ): KoniferV2Result<AssetResponse> =
        assets(path)
            .newAsset()
            .fromBytes(bytes, format)
            .withMetadata(request)
            .store()

    suspend fun KoniferClientV2.storeAsset(
        path: String,
        request: StoreAssetRequest,
    ): KoniferV2Result<AssetResponse> {
        val source = assets(path).newAsset()
        val url = request.source.http.url ?: request.url
        return when {
            !url.isNullOrBlank() -> source.fromUrl(url)

            !request.source.s3.arn
                .isNullOrBlank() -> source.fromS3Arn(request.source.s3.arn!!)

            else -> throw IllegalArgumentException("Either http.url or s3.arn is required in request")
        }.withMetadata(request).store()
    }

    suspend fun KoniferClientV2.fetchAssetInfo(
        path: String,
        querySelectors: FetchQuerySelector = None,
        labels: Map<String, String> = emptyMap(),
    ): KoniferV2Result<AssetResponse> = assetSelection(path, querySelectors, labels).fetchInfo()

    suspend fun KoniferClientV2.fetchAssetContentBytes(
        path: String,
        querySelectors: FetchQuerySelector = None,
        labels: Map<String, String> = emptyMap(),
        requestedTransformation: RequestedTransformation = RequestedTransformation.OriginalVariant,
        delivery: ContentDelivery = ContentDelivery.THROUGH_KONIFER,
    ): KoniferV2Result<ByteArray> =
        assetSelection(path, querySelectors, labels)
            .variant(requestedTransformation)
            .fetchContentBytes(delivery)

    suspend fun KoniferClientV2.fetchAssetLink(
        path: String,
        querySelectors: FetchQuerySelector = None,
        labels: Map<String, String> = emptyMap(),
        requestedTransformation: RequestedTransformation = RequestedTransformation.OriginalVariant,
    ): KoniferV2Result<AssetLinkResponse> =
        assetSelection(path, querySelectors, labels)
            .variant(requestedTransformation)
            .fetchLink()

    suspend fun KoniferClientV2.deleteAsset(
        path: String,
        querySelectors: DeleteQuerySelector = None,
        labels: Map<String, String> = emptyMap(),
        limit: Int = 1,
    ): KoniferV2Result<Unit> =
        when (querySelectors) {
            is EntryId -> {
                require(labels.isEmpty()) { "Entry selection with labels is not supported by KoniferClientV2" }
                assets(path).entry(querySelectors.entryId).deleteFirst()
            }

            is OrderBy -> {
                assets(path).orderBy(querySelectors.orderBy).matchingLabels(labels).deleteFirst(limit)
            }

            None -> {
                assets(path).matchingLabels(labels).deleteFirst(limit)
            }

            Recursive -> {
                assets(path).matchingLabels(labels).deleteRecursively()
            }
        }

    suspend fun KoniferClientV2.evaluateRules(
        format: ImageFormat,
        request: EvaluateRuleDefinitionsRequest,
        bytes: ByteArray,
    ): KoniferV2Result<EvaluateRuleDefinitionsResponse> =
        ruleEvaluation()
            .fromBytes(bytes, format)
            .withDefinitions(request.definitions)
            .evaluate()

    suspend fun KoniferClientV2.evaluateRules(request: EvaluateRuleDefinitionsRequest): KoniferV2Result<EvaluateRuleDefinitionsResponse> {
        val url = request.source.http.url ?: request.url
        val source =
            when {
                !url.isNullOrBlank() -> ruleEvaluation().fromUrl(url)

                !request.source.s3.arn
                    .isNullOrBlank() -> ruleEvaluation().fromS3Arn(request.source.s3.arn!!)

                else -> throw IllegalArgumentException("Either http.url or s3.arn is required in request")
            }
        return source.withDefinitions(request.definitions).evaluate()
    }

    inline fun <T, R> KoniferV2Result<T>.fold(
        onSuccess: (T) -> R,
        onError: (statusCode: Int?, message: String?, cause: Throwable?) -> R,
    ): R =
        when (this) {
            is KoniferV2Result.Success -> onSuccess(value)
            is KoniferV2Result.Failure.Http -> onError(statusCode, message, null)
            is KoniferV2Result.Failure.Transport -> onError(null, null, cause)
            is KoniferV2Result.Failure.InvalidResponse -> onError(null, null, cause)
        }

    val <T> KoniferV2Result.Success<T>.body: T
        get() = value

    private fun KoniferClientV2.assetSelection(
        path: String,
        querySelector: FetchQuerySelector,
        labels: Map<String, String>,
    ): AssetSelection =
        when (querySelector) {
            is EntryId -> {
                require(labels.isEmpty()) { "Entry selection with labels is not supported by KoniferClientV2" }
                assets(path).entry(querySelector.entryId)
            }

            is OrderBy -> {
                assets(path).orderBy(querySelector.orderBy).matchingLabels(labels)
            }

            None -> {
                assets(path).matchingLabels(labels)
            }
        }

    private fun NewAssetAtPath.withMetadata(request: StoreAssetRequest): NewAssetAtPath {
        var asset = this
        request.alt?.let { asset = asset.withAlt(it) }
        asset = asset.withLabels(request.labels)
        asset = asset.withTags(request.tags)
        return asset
    }
}
