package io.direkt.domain.ports

import io.direkt.asset.model.AssetAndVariants
import io.direkt.image.model.LQIPImplementation
import io.direkt.image.model.ImageFormat
import io.direkt.image.model.PreProcessedImage
import io.direkt.image.model.RequestedTransformation
import io.direkt.image.model.Transformation
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
