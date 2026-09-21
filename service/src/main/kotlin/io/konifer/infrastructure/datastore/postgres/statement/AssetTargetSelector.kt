package io.konifer.infrastructure.datastore.postgres.statement

import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Select
import java.util.UUID

typealias AssetTargetSelector = context(DSLContext)
() -> Select<Record1<UUID?>>
