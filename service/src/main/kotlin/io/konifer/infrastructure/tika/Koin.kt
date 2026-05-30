package io.konifer.infrastructure.tika

import io.konifer.domain.ports.MimeTypeDetector
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

fun mimeTypeDetectorModule(): Module =
    module {
        single<TikaMimeTypeDetector>() bind MimeTypeDetector::class
    }
