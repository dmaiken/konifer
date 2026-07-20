package io.konifer.infrastructure.work

import kotlinx.coroutines.CompletableDeferred

sealed interface WorkItem<T> {
    val deferredResult: CompletableDeferred<T>?
}
