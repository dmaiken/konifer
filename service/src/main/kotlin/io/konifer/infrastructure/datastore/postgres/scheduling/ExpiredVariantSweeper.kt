package io.konifer.infrastructure.datastore.postgres.scheduling

import io.konifer.infrastructure.datastore.postgres.PostgresVariantRepository
import io.ktor.util.logging.KtorSimpleLogger

/**
 * Deletes expired variants from the DB
 */
object ExpiredVariantSweeper {
    const val TASK_NAME = "expired-variant-sweeper"
    private val logger = KtorSimpleLogger(this::class.qualifiedName!!)

    suspend fun invoke(postgresVariantRepository: PostgresVariantRepository) {
        logger.info("Sweeping expired variants...")

        runCatching {
            postgresVariantRepository.deleteExpiredVariants()
        }.onFailure { e ->
            logger.error("$TASK_NAME failed", e)
        }.onSuccess { count ->
            logger.info("Swept $count expired variants")
        }
    }
}
