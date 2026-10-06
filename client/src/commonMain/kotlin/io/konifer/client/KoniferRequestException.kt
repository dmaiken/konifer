package io.konifer.client

class KoniferRequestException(
    val failure: KoniferResult.Failure,
) : RuntimeException(
        when (failure) {
            is KoniferResult.Failure.Http -> {
                "Konifer returned HTTP ${failure.statusCode}: ${failure.message.orEmpty()}"
            }

            is KoniferResult.Failure.Transport -> {
                "Konifer request failed during transport"
            }

            is KoniferResult.Failure.InvalidResponse -> {
                "Konifer response could not be decoded"
            }
        },
        when (failure) {
            is KoniferResult.Failure.Http -> null
            is KoniferResult.Failure.Transport -> failure.cause
            is KoniferResult.Failure.InvalidResponse -> failure.cause
        },
    )
