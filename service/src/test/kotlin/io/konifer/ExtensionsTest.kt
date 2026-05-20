package io.konifer

import com.typesafe.config.ConfigFactory
import io.konifer.infrastructure.datastore.DataStoreProvider
import io.konifer.infrastructure.getDataStoreProvider
import io.konifer.infrastructure.getObjectStoreProvider
import io.konifer.infrastructure.objectstore.ObjectStoreProvider
import io.kotest.matchers.shouldBe
import io.ktor.server.config.HoconApplicationConfig
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junitpioneer.jupiter.SetEnvironmentVariable

class ExtensionsTest {
    @Nested
    inner class GetDataStoreProviderTests {
        @Test
        @SetEnvironmentVariable(key = "IN_MEMORY", value = "true")
        fun `if in_memory is true then provider is in_memory`() {
            val config =
                """
                data-store {
                  provider = postgresql
                  postgresql {
                    user = "user"
                  }
                }
                """.trimIndent()

            HoconApplicationConfig(ConfigFactory.parseString(config))
                .getDataStoreProvider() shouldBe DataStoreProvider.IN_MEMORY
        }

        @Test
        @SetEnvironmentVariable(key = "IN_MEMORY", value = "false")
        fun `if in_memory is false then provider is whatever is configured`() {
            val config =
                """
                data-store {
                  provider = postgresql
                  postgresql {
                    user = "user"
                  }
                }
                """.trimIndent()

            HoconApplicationConfig(ConfigFactory.parseString(config))
                .getDataStoreProvider() shouldBe DataStoreProvider.POSTGRES
        }

        @Test
        fun `if in_memory is not supplied then provider is whatever is configured`() {
            val config =
                """
                data-store {
                  provider = postgresql
                  postgresql {
                    user = "user"
                  }
                }
                """.trimIndent()

            HoconApplicationConfig(ConfigFactory.parseString(config))
                .getDataStoreProvider() shouldBe DataStoreProvider.POSTGRES
        }

        @Test
        fun `if nothing is supplied then provider is default`() {
            HoconApplicationConfig(ConfigFactory.parseString(""))
                .getDataStoreProvider() shouldBe DataStoreProvider.POSTGRES
        }
    }

    @Nested
    inner class GetObjectStoreProviderTests {
        @Test
        @SetEnvironmentVariable(key = "IN_MEMORY", value = "true")
        fun `if in_memory is true then provider is in_memory`() {
            val config =
                """
                object-store {
                  provider = s3
                
                  s3 {
                    endpoint-url = "http://minio:9000"
                    region = "auto"
                    access-key = "minio_admin"                
                    force-path-style = true
                  }
                }
                """.trimIndent()

            HoconApplicationConfig(ConfigFactory.parseString(config))
                .getObjectStoreProvider() shouldBe ObjectStoreProvider.IN_MEMORY
        }

        @Test
        @SetEnvironmentVariable(key = "IN_MEMORY", value = "false")
        fun `if in_memory is false then provider is whatever is configured`() {
            val config =
                """
                object-store {
                  provider = s3
                
                  s3 {
                    endpoint-url = "http://minio:9000"
                    region = "auto"
                    access-key = "minio_admin"                
                    force-path-style = true
                  }
                }
                """.trimIndent()

            HoconApplicationConfig(ConfigFactory.parseString(config))
                .getObjectStoreProvider() shouldBe ObjectStoreProvider.S3
        }

        @Test
        fun `if in_memory is not supplied then provider is whatever is configured`() {
            val config =
                """
                object-store {
                  provider = s3
                
                  s3 {
                    endpoint-url = "http://minio:9000"
                    region = "auto"
                    access-key = "minio_admin"                
                    force-path-style = true
                  }
                }
                """.trimIndent()

            HoconApplicationConfig(ConfigFactory.parseString(config))
                .getObjectStoreProvider() shouldBe ObjectStoreProvider.S3
        }

        @Test
        fun `if nothing is supplied then provider is default`() {
            HoconApplicationConfig(ConfigFactory.parseString(""))
                .getObjectStoreProvider() shouldBe ObjectStoreProvider.FILESYSTEM
        }
    }
}
