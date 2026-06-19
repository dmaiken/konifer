package io.konifer.infrastructure.datastore.postgres.scheduling

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * These control scheduled job properties. This is intentionally not documented in the public config API
 * because I currently only use them for testing these jobs.
 */
@Serializable
data class ScheduledJobProperties(
    val jobPollingInterval: Duration = 10.seconds, // default in db-scheduler
    val failedAssetSweeperInterval: Duration = 1.minutes,
    val failedVariantSweeperInterval: Duration = 1.minutes,
    val variantReaperInterval: Duration = 30.seconds,
    val expiredVariantsSweeperInterval: Duration = 30.seconds,
    val variantMetricsFlushInterval: Duration = 30.seconds,
)
