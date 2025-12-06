package io.direkt.domain.ports

import io.direkt.asset.model.AssetAndVariants
import io.direkt.domain.image.LQIPImplementation
import io.direkt.domain.image.ImageFormat
import io.direkt.domain.image.PreProcessedImage
import io.direkt.domain.image.RequestedTransformation
import io.direkt.domain.image.Transformation
import kotlinx.coroutines.CompletableDeferred
import java.io.File

interface VariantGenerator {
    suspend fun preProcessOriginalVariant(
        sourceFormat: ImageFormat,
        lqipImplementations: Set<LQIPImplementation>,
        transformation: Transformation,
        source: File,
    ): CompletableDeferred<PreProcessedImage>

    suspend fun initiateEagerVariants(
        path: String,
        entryId: Long,
        requestedTransformations: List<RequestedTransformation>,
        lqipImplementations: Set<LQIPImplementation>,
        bucket: String,
    )

    suspend fun generateOnDemandVariant(
        path: String,
        entryId: Long,
        lqipImplementations: Set<LQIPImplementation>,
        bucket: String,
        transformation: Transformation,
    ): CompletableDeferred<AssetAndVariants>
}
