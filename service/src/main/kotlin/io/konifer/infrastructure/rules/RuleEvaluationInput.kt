package io.konifer.infrastructure.rules

import app.photofox.vipsffm.VImage

@JvmInline
value class RuleEvaluationInput(
    val vImage: VImage,
)
