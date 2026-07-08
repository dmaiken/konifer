package io.konifer.infrastructure.rules.inference

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.ktor.util.logging.KtorSimpleLogger
import kotlin.io.path.pathString
import kotlin.time.measureTimedValue

class OnnxSessionFactory(
    private val ortEnvironment: OrtEnvironment,
) {
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    fun create(model: Model): OrtSession =
        when (model) {
            Model.SIGLIP2_TEXT -> {
                logger.info("Creating Siglip2 text model session")
                measureTimedValue {
                    ortEnvironment
                        .createSession(
                            Siglip2ModelFiles.textModel().pathString,
                            OrtSession.SessionOptions(),
                        )
                }.let {
                    logger.info("Created Siglip2 text model session in ${it.duration.inWholeMilliseconds}ms")
                    it.value
                }
            }

            Model.SIGLIP2_VISION -> {
                logger.info("Creating Siglip2 vision model session")
                measureTimedValue {
                    ortEnvironment
                        .createSession(
                            Siglip2ModelFiles.visionModel().pathString,
                        )
                }.let {
                    logger.info("Created Siglip2 vision model session in ${it.duration.inWholeMilliseconds}ms")
                    it.value
                }
            }
        }
}
