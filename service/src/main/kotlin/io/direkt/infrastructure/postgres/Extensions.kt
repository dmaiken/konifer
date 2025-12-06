package io.direkt.infrastructure.postgres

import org.jooq.Field
import org.jooq.Record

fun <T : Any> Record.getNonNull(field: Field<T?>): T = checkNotNull(this.get(field)) { "Field '${field.name}' is null" }
