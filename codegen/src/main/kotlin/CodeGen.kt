import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions.DATABASE
import io.r2dbc.spi.ConnectionFactoryOptions.DRIVER
import io.r2dbc.spi.ConnectionFactoryOptions.HOST
import io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD
import io.r2dbc.spi.ConnectionFactoryOptions.PORT
import io.r2dbc.spi.ConnectionFactoryOptions.PROTOCOL
import io.r2dbc.spi.ConnectionFactoryOptions.USER
import io.r2dbc.spi.ConnectionFactoryOptions.builder
import name.nkonev.r2dbc.migrate.core.R2dbcMigrate
import name.nkonev.r2dbc.migrate.core.R2dbcMigrateProperties
import name.nkonev.r2dbc.migrate.reader.ReflectionsClasspathResourceReader
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target
import org.testcontainers.containers.PostgreSQLContainer

fun main() {
    // 1. Start Postgres container
    val pg = PostgreSQLContainer("postgres:15").apply {
        start()
        println("PostgreSQL Container started")
    }
    val r2dbcUrl = "r2dbc:postgresql://${pg.host}:${pg.getMappedPort(5432)}/${pg.databaseName}"
    println("Running migrations on: $r2dbcUrl")

    // 2. Run migrations with r2dbc-migrate
    val options = builder()
        .option(DATABASE, pg.databaseName)
        .option(DRIVER, "postgresql")
        .option(PROTOCOL, "postgresql")
        .option(USER, pg.username)
        .option(PASSWORD, pg.password)
        .option(HOST, pg.host)
        .option(PORT, pg.getMappedPort(5432))
        .build()

    val migrateProperties =
        R2dbcMigrateProperties().apply {
            setResourcesPath("db/migration")
        }

    R2dbcMigrate.migrate(
        ConnectionFactories.get(options),
        migrateProperties,
        ReflectionsClasspathResourceReader(),
        null,
        null
    ).block()

    val configuration = Configuration().apply {
        jdbc = Jdbc().apply {
            driver = "org.postgresql.Driver"
            url = pg.jdbcUrl
            user = pg.username
            password = pg.password
        }
        generator = Generator().apply {
            name = "org.jooq.codegen.KotlinGenerator"
            database = Database().apply {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                inputSchema = "public"
                excludes = "migrations|scheduled_tasks"
            }
            target = Target().apply {
                packageName = "direkt.jooq"
                directory = "../jooq-generated/src/main/kotlin"
            }
            generate = Generate().apply {
                withPojos(true)
                withKotlinNotNullInterfaceAttributes(true)
                withKotlinNotNullPojoAttributes(true)
                withPojosAsKotlinDataClasses(true)
            }
        }
    }
    GenerationTool.generate(configuration)

    pg.stop()
}