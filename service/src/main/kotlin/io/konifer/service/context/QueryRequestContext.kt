package io.konifer.service.context

import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.variant.Transformation
import io.konifer.service.context.selector.QuerySelectors
import io.ktor.http.Parameters

data class QueryRequestContext(
    val path: String,
    val pathConfiguration: PathConfiguration,
    val selectors: QuerySelectors,
    val transformation: Transformation?,
    val labels: Map<String, String>,
    val request: HttpRequest,
)

data class HttpRequest(
    val parameters: Parameters,
)
