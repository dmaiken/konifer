package io.konifer.client

class AssetContentRequestOptions private constructor(
    val querySelectors: QuerySelectors,
    val requestedTransformation: RequestedTransformation,
    val fetchMode: ContentFetchMode,
) {
    class Builder {
        private var querySelectors: QuerySelectors = QuerySelectors.None()
        private var requestedTransformation: RequestedTransformation = RequestedTransformation.OriginalVariant
        private var fetchMode: ContentFetchMode = ContentFetchMode.CONTENT

        fun querySelectors(value: QuerySelectors) = apply { querySelectors = value }

        fun requestedTransformation(value: RequestedTransformation) = apply { requestedTransformation = value }

        fun fetchMode(value: ContentFetchMode) = apply { fetchMode = value }

        fun build() =
            AssetContentRequestOptions(
                querySelectors,
                requestedTransformation,
                fetchMode,
            )
    }
}
