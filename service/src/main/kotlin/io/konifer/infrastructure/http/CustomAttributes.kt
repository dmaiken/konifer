package io.konifer.infrastructure.http

import io.ktor.util.AttributeKey
import java.time.LocalDateTime

object CustomAttributes {
    val entryIdKey = AttributeKey<Long>("entryIdKey")
    val lastModifiedKey = AttributeKey<LocalDateTime>("lastModifiedKey")
}
