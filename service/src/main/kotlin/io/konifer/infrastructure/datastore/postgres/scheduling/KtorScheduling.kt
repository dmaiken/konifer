package io.konifer.infrastructure.datastore.postgres.scheduling

import com.github.kagkarlsson.scheduler.Scheduler
import com.github.kagkarlsson.scheduler.task.helper.Tasks
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.ports.ObjectStore
import io.konifer.infrastructure.datastore.postgres.PostgresProperties
import io.konifer.infrastructure.datastore.postgres.PostgresVariantRepository
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.log
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.koin.ktor.ext.inject
import org.postgresql.ds.PGSimpleDataSource
import java.util.concurrent.Executors
import kotlin.time.toJavaDuration

fun Application.configureScheduledJobs(
    postgresProperties: PostgresProperties,
    dslContext: DSLContext,
    scheduledJobProperties: ScheduledJobProperties,
) {
    val objectStore by inject<ObjectStore>()
    val assetRepository by inject<AssetRepository>()
    val variantRepository by inject<PostgresVariantRepository>()

    log.info("Scheduled job time is: $scheduledJobProperties")

    val failedAssetSweeperTask =
        Tasks
            .recurring(FailedAssetSweeper.TASK_NAME, FixedDelay.of(scheduledJobProperties.failedAssetSweeperInterval.toJavaDuration()))
            .execute { _, _ ->
                runBlocking {
                    FailedAssetSweeper.invoke(
                        dslContext = dslContext,
                        assetRepository = assetRepository,
                    )
                }
            }
    val failedVariantSweeperTask =
        Tasks
            .recurring(FailedVariantSweeper.TASK_NAME, FixedDelay.of(scheduledJobProperties.failedVariantSweeperInterval.toJavaDuration()))
            .execute { _, _ ->
                runBlocking {
                    FailedVariantSweeper.invoke(dslContext)
                }
            }
    val variantReaperTask =
        Tasks
            .recurring(VariantReaper.TASK_NAME, FixedDelay.of(scheduledJobProperties.variantReaperInterval.toJavaDuration()))
            .execute { _, _ ->
                runBlocking {
                    VariantReaper.invoke(
                        dslContext = dslContext,
                        objectStore = objectStore,
                    )
                }
            }
    val expiredVariantsSweeperTask =
        Tasks
            .recurring(
                ExpiredVariantSweeper.TASK_NAME,
                FixedDelay.of(scheduledJobProperties.expiredVariantsSweeperInterval.toJavaDuration()),
            ).execute { _, _ ->
                runBlocking {
                    ExpiredVariantSweeper.invoke(
                        postgresVariantRepository = variantRepository,
                    )
                }
            }

    val schedulerDataSource = jdbcPostgresDatasource(postgresProperties)
    val schedulerExecutor = Executors.newVirtualThreadPerTaskExecutor()
    val scheduler =
        Scheduler
            .create(schedulerDataSource)
            .pollingInterval(scheduledJobProperties.jobPollingInterval.toJavaDuration())
            .executorService(schedulerExecutor)
            .serializer(KotlinSerializer())
            .startTasks(failedAssetSweeperTask, failedVariantSweeperTask, variantReaperTask, expiredVariantsSweeperTask)
            .build()

    monitor.subscribe(ApplicationStarted) {
        scheduler.start()
    }

    // Stop the scheduler when the app shuts down
    monitor.subscribe(ApplicationStopping) {
        log.info("Shutting down scheduler...")
        scheduler.stop()
        schedulerExecutor.shutdown()
        schedulerDataSource.close()
    }
}

fun jdbcPostgresDatasource(properties: PostgresProperties): HikariDataSource {
    val dataSource = PGSimpleDataSource()
    dataSource.setServerNames(arrayOf(properties.host))
    dataSource.setPortNumbers(intArrayOf(properties.port))
    dataSource.databaseName = properties.database
    dataSource.user = properties.user
    dataSource.password = properties.password

    return HikariDataSource(
        HikariConfig().apply {
            this.dataSource = dataSource
            maximumPoolSize = 3
            minimumIdle = 1
        },
    )
}
