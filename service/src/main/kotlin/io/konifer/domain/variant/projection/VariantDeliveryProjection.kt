package io.konifer.domain.variant.projection

import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.VariantId
import io.konifer.domain.variant.VariantSpecification
import io.konifer.domain.variant.attribute.Attributes

interface VariantDeliveryProjection {
    val id: VariantId
    val objectStoreBucket: String
    val objectStoreKey: String
    val attributes: Attributes
    val specification: VariantSpecification
    val lqips: LQIPs
    val isOriginalVariant: Boolean
}
