package io.konifer.domain.event

import io.konifer.domain.path.PathConfiguration
import io.konifer.domain.variant.Variant
import java.nio.file.Path

data class AssetReadyEvent(
    val pathConfiguration: PathConfiguration,
    val originalVariantFile: Path?,
    val originalVariant: Variant,
) : DomainEvent
