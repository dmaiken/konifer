package io.konifer.infrastructure.http

import io.konifer.domain.variant.VariantId
import io.ktor.util.AttributeKey
import java.time.Instant

object CustomAttributes {
    val variantIdKey = AttributeKey<VariantId>("variantIdKey")
    val lastModifiedKey = AttributeKey<Instant>("lastModifiedKey")
}
