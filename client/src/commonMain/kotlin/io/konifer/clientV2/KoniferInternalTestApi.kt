package io.konifer.clientV2

/** Marks client APIs intended only for Konifer's internal test harnesses. */
@RequiresOptIn(level = RequiresOptIn.Level.ERROR, message = "This API is for internal testing only and should not be used in production.")
@Retention(AnnotationRetention.BINARY)
annotation class KoniferInternalTestApi
