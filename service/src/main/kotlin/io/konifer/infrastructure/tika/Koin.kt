package io.konifer.infrastructure.tika

import io.konifer.domain.ports.MimeTypeDetector
import org.koin.core.module.Module
import org.koin.dsl.module

fun mimeTypeDetectorModule(): Module =
    module {
        single<MimeTypeDetector> {
            TikaMimeTypeDetector()
        }
    }
