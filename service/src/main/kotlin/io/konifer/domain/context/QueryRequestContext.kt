package io.konifer.domain.context

import io.konifer.domain.context.selector.QuerySelectors
import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.variant.VariantSpecification
import io.ktor.http.Parameters
import io.ktor.http.RequestConnectionPoint

data class QueryRequestContext(
    val path: String,
    val pathConfiguration: PathConfiguration,
    val selectors: QuerySelectors,
    val specification: VariantSpecification?,
    val labels: Map<String, String>,
    val request: HttpRequest,
) : RequestContext

data class HttpRequest(
    val parameters: Parameters,
    val origin: RequestConnectionPoint,
)
