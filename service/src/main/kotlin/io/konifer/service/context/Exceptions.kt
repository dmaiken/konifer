package io.konifer.service.context

open class InvalidPathException(
    msg: String,
    e: Throwable? = null,
) : RuntimeException(msg, e)

class InvalidQuerySelectorsException(
    msg: String,
    e: Throwable? = null,
) : InvalidPathException(msg, e)

class InvalidDeleteSelectorsException(
    msg: String,
    e: Throwable? = null,
) : InvalidPathException(msg, e)

class ContentTypeNotPermittedException(
    msg: String,
    e: Throwable? = null,
) : RuntimeException(msg, e)
