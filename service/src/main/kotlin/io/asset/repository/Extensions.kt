package io.asset.repository

import org.jooq.Field
import org.jooq.Record

fun <T : Any> Record.getNonNull(field: Field<T?>): T {
    return checkNotNull(this.get(field)) { "Field '${field.name}' is null" }
}
