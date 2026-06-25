package io.konifer.infrastructure.rules

import app.photofox.vipsffm.VImage
import java.nio.file.Path

@JvmInline
value class RuleEvaluationInput(
    val vImage: VImage,
)
