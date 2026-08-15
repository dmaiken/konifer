package io.konifer.infrastructure.datastore.postgres

import io.konifer.common.selector.Order
import io.konifer.domain.ports.AssetRepository
import io.konifer.domain.ports.DeleteAssetsCommand
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PostgresAssetDeleterTest {
    private val assetRepository = mockk<AssetRepository>(relaxed = true)
    private val assetDeleter = PostgresAssetDeleter(assetRepository)

    @Test
    fun `delegates entry deletion to repository`() =
        runTest {
            assetDeleter.delete(DeleteAssetsCommand.Entry(path = "/users/123", entryId = 4))

            coVerify(exactly = 1) { assetRepository.deleteByPath(path = "/users/123", entryId = 4) }
        }

    @Test
    fun `delegates path deletion to repository`() =
        runTest {
            assetDeleter.delete(
                DeleteAssetsCommand.AtPath(
                    path = "/users/123",
                    labels = mapOf("animal" to "cat"),
                    order = Order.MODIFIED,
                    limit = 5,
                ),
            )

            coVerify(exactly = 1) {
                assetRepository.deleteAllByPath(
                    path = "/users/123",
                    labels = mapOf("animal" to "cat"),
                    order = Order.MODIFIED,
                    limit = 5,
                )
            }
        }

    @Test
    fun `delegates recursive deletion to repository`() =
        runTest {
            assetDeleter.delete(
                DeleteAssetsCommand.Recursively(
                    path = "/users/123",
                    labels = mapOf("animal" to "cat"),
                ),
            )

            coVerify(exactly = 1) {
                assetRepository.deleteRecursivelyByPath(
                    path = "/users/123",
                    labels = mapOf("animal" to "cat"),
                )
            }
        }
}
