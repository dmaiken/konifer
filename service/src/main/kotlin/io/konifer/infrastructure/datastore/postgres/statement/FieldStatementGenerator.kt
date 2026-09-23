package io.konifer.infrastructure.datastore.postgres.statement

import org.jooq.Field
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import java.time.LocalDateTime

object FieldStatementGenerator {
    fun currentUtcLocalDateTime(): Field<LocalDateTime> =
        DSL.field(
            "(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')",
            SQLDataType.LOCALDATETIME,
        )
}
