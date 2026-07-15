package io.konifer.infrastructure.rules.inference

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString

object Siglip2ModelFiles {
    private const val DEFAULT_MODEL_DIR = "models/siglip2-base-patch16-224"
    private const val PARENT_DEFAULT_MODEL_DIR = "../$DEFAULT_MODEL_DIR"
    private const val VISION_MODEL = "vision_model.onnx"
    private const val TEXT_MODEL = "text_model.onnx"
    private const val TOKENIZER = "tokenizer.json"

    fun modelDir(): Path =
        listOf(
            Path.of(DEFAULT_MODEL_DIR),
            Path.of(PARENT_DEFAULT_MODEL_DIR),
        ).firstOrNull { Files.isDirectory(it) }
            ?: Path.of(DEFAULT_MODEL_DIR)

    fun visionModel(): Path = requiredFile(VISION_MODEL)

    fun textModel(): Path = requiredFile(TEXT_MODEL)

    fun tokenizer(): Path = requiredFile(TOKENIZER)

    private fun requiredFile(fileName: String): Path =
        modelDir()
            .resolve(fileName)
            .also { path ->
                require(Files.isRegularFile(path)) {
                    "SigLIP2 model file not found at '${path.pathString}'. " +
                        "Run scripts/download-siglip2-models.sh to create $DEFAULT_MODEL_DIR containing " +
                        "$VISION_MODEL, $TEXT_MODEL, and $TOKENIZER."
                }
            }
}
