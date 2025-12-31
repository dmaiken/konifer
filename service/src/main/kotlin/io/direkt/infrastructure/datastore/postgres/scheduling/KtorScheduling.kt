package io.direkt.infrastructure.datastore.postgres.scheduling

import com.github.kagkarlsson.scheduler.Scheduler
import com.github.kagkarlsson.scheduler.task.helper.Tasks
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.direkt.domain.ports.ObjectRepository
import io.direkt.infrastructure.datastore.postgres.PostgresProperties
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.log
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.koin.ktor.ext.get
import org.koin.ktor.ext.inject
import org.postgresql.ds.PGSimpleDataSource
import java.util.concurrent.Executors
import javax.sql.DataSource

fun Application.configureScheduling(
    postgresProperties: PostgresProperties,
    dslContext: DSLContext,
) {
    val objectRepository by inject<ObjectRepository>()

    // Tasks
    // failed asset sweeper - assets with no original variant that has been uploaded (after 5 minutes?) (cron) - delete row and schedule grim reaper in one transaction
    // failed variant sweeper - variants not uploaded (cron) - delete row and schedule grim reaper in one transaction
    // grim reaper - delete object from object store

    val failedAssetSweeperTask =
        Tasks
            .recurring(FailedAssetSweeper.TASK_NAME, FixedDelay.ofMinutes(1))
            .execute { _, _ ->
                runBlocking {
                    FailedAssetSweeper.invoke(dslContext)
                }
            }
    val failedVariantSweeperTask =
        Tasks
            .recurring(FailedVariantSweeper.TASK_NAME, FixedDelay.ofMinutes(1))
            .execute { _, _ ->
                runBlocking {
                    FailedVariantSweeper.invoke(dslContext)
                }
            }
    val variantReaperTask =
        Tasks
            .recurring(VariantReaper.TASK_NAME, FixedDelay.ofSeconds(30))
            .execute { _, _ ->
                runBlocking {
                    VariantReaper.invoke(
                        dslContext = dslContext,
                        objectRepository = objectRepository,
                    )
                }
            }

    val scheduler =
        Scheduler
            .create(jdbcPostgresDatasource(postgresProperties))
            .executorService(Executors.newVirtualThreadPerTaskExecutor())
            .serializer(KotlinSerializer())
            .startTasks(failedAssetSweeperTask, failedVariantSweeperTask, variantReaperTask)
            .build()

    monitor.subscribe(ApplicationStarted) {
        scheduler.start()
    }

    // Stop the scheduler when the app shuts down
    monitor.subscribe(ApplicationStopping) {
        log.info("Shutting down scheduler...")
        scheduler.stop()
    }
}

fun jdbcPostgresDatasource(properties: PostgresProperties): DataSource {
    val dataSource = PGSimpleDataSource()
    dataSource.setServerNames(arrayOf(properties.host))
    dataSource.setPortNumbers(intArrayOf(properties.port))
    dataSource.databaseName = properties.database
    dataSource.user = properties.user
    properties.password?.let {
        dataSource.password = it
    }

    return HikariDataSource(
        HikariConfig().apply {
            this.dataSource = dataSource
        },
    )
}
