package io.konifer.domain.ports

import io.konifer.common.image.ImageFormat
import io.konifer.domain.image.LQIPImplementation
import io.konifer.domain.rules.upload.UploadRuleset
import kotlinx.coroutines.CompletableDeferred
import java.nio.file.Path

interface OriginalVariantContentProcessor {

    suspend fun process(
        sourceFormat: ImageFormat,
        lqipImplementations: Set<LQIPImplementation>,
        source: Path,
        transformationDataContainer: TransformationDataContainer,
        uploadRuleset: UploadRuleset,
    ): CompletableDeferred<ContentProcessorResult>
}

sealed interface ContentProcessorResult {
    class Success: ContentProcessorResult
    class Failure(val exception: Throwable): ContentProcessorResult
}
