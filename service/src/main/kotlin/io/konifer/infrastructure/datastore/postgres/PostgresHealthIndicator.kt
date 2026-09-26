package io.konifer.infrastructure.datastore.postgres

import io.konifer.infrastructure.health.CachingHealthIndicator
import io.konifer.infrastructure.health.HealthIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.reactive.awaitSingle
import org.jooq.DSLContext

class PostgresHealthIndicator(
    scope: CoroutineScope,
    dslContext: DSLContext,
) : HealthIndicator by CachingHealthIndicator(
        name = "postgres",
        scope = scope,
        healthCheck = { dslContext.selectOne().awaitSingle().value1() == 1 },
    )
