package io.konifer.client

class AssetContentRequestOptions private constructor(
    val querySelectors: FetchQuerySelector,
    val requestedTransformation: RequestedTransformation,
    val labels: Map<String, String>,
    val fetchMode: ContentFetchMode,
) {
    class Builder {
        private var querySelectors: FetchQuerySelector = None()
        private var requestedTransformation: RequestedTransformation = RequestedTransformation.OriginalVariant
        private var fetchMode: ContentFetchMode = ContentFetchMode.CONTENT
        private var labels: Map<String, String> = emptyMap()

        fun querySelectors(value: FetchQuerySelector) = apply { querySelectors = value }

        fun requestedTransformation(value: RequestedTransformation) = apply { requestedTransformation = value }

        fun fetchMode(value: ContentFetchMode) = apply { fetchMode = value }

        fun labels(value: Map<String, String>) = apply { labels = value }

        fun build() =
            AssetContentRequestOptions(
                querySelectors = querySelectors,
                requestedTransformation = requestedTransformation,
                labels = labels,
                fetchMode = fetchMode,
            )
    }
}
