package io.direkt.infrastructure.datastore.postgres.scheduling

import com.github.kagkarlsson.scheduler.Scheduler
import com.github.kagkarlsson.scheduler.task.helper.Tasks
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.direkt.infrastructure.datastore.postgres.PostgresProperties
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.util.logging.KtorSimpleLogger
import org.postgresql.ds.PGSimpleDataSource
import java.util.concurrent.Executors

fun Application.configureScheduling(postgresProperties: PostgresProperties) {
    val logger = KtorSimpleLogger("io.direkt.infrastructure.scheduling")

    log.info("(JDBC) Connecting to postgres database")
    val ds = PGSimpleDataSource()
    ds.setServerNames(arrayOf(postgresProperties.host))
    ds.setPortNumbers(intArrayOf(postgresProperties.port))
    ds.databaseName = postgresProperties.database
    ds.user = postgresProperties.user
    postgresProperties.password?.let {
        ds.password = it
    }
    val config =
        HikariConfig().apply {
            dataSource = ds
        }

    val myTask =
        Tasks
            .oneTime("my-task")
            .execute { taskInstance, executionContext ->
                // Your task logic here (e.g., send push notification, interact with Ktor code)
                logger.info("Executing task: ${taskInstance.id}")
            }

    // Tasks
    // failed asset sweeper - assets with no original variant that has been uploaded (after 5 minutes?) (cron) - delete row and schedule grim reaper in one transaction
    // failed variant sweeper - variants not uploaded (cron) - delete row and schedule grim reaper in one transaction
    // grim reaper - delete object from object store

    val scheduler =
        Scheduler
            .create(HikariDataSource(config))
            .executorService(Executors.newVirtualThreadPerTaskExecutor())
            .serializer(KotlinSerializer())
            .build()

    scheduler.start()

    // Optional: Stop the scheduler when the Ktor app shuts down
//    EmbeddedServer.monitor.subscribe(ApplicationStopping) {
//        scheduler.stop()
//    }
}
