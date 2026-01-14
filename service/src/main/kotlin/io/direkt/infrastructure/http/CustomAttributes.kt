package io.direkt.infrastructure.http

import io.direkt.service.context.DeleteRequestContext
import io.direkt.service.context.QueryRequestContext
import io.direkt.service.context.UpdateRequestContext
import io.ktor.util.AttributeKey
import java.time.LocalDateTime

object CustomAttributes {
    val queryRequestContextKey = AttributeKey<QueryRequestContext>("queryRequestContextKey")
    val updateRequestContextKey = AttributeKey<UpdateRequestContext>("updateRequestContextKey")
    val deleteRequestContextKey = AttributeKey<DeleteRequestContext>("deleteRequestContextKey")

    val entryIdKey = AttributeKey<Long>("entryIdKey")
    val lastModifiedKey = AttributeKey<LocalDateTime>("lastModifiedKey")
}
