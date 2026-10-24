package io.konifer.infrastructure.datastore.inmemory.projection

import io.konifer.domain.variant.LQIPs
import io.konifer.domain.variant.VariantData
import io.konifer.domain.variant.VariantId
import io.konifer.domain.variant.VariantSpecification
import io.konifer.domain.variant.attribute.Attributes
import io.konifer.domain.variant.projection.VariantDeliveryProjection

class InMemoryVariantDeliveryProjection(
    variantData: VariantData,
) : VariantDeliveryProjection {
    override val id: VariantId = variantData.id

    override val objectStoreBucket: String = variantData.objectStoreBucket

    override val objectStoreKey: String = variantData.objectStoreKey

    override val attributes: Attributes = variantData.attributes

    override val lqips: LQIPs = variantData.lqips

    override val specification: VariantSpecification = variantData.specification

    override val isOriginalVariant: Boolean = variantData.isOriginalVariant
}
