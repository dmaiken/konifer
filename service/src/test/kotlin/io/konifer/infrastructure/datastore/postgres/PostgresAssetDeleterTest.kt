package io.konifer.infrastructure.datastore.postgres

import io.konifer.domain.asset.AssetPath
import io.konifer.domain.asset.toAssetLabels
import io.konifer.domain.asset.toEntryId
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
            assetDeleter.delete(DeleteAssetsCommand.Entry(path = AssetPath("/users/123"), entryId = 4L.toEntryId()))

            coVerify(exactly = 1) { assetRepository.deleteByPath(path = AssetPath("/users/123"), entryId = 4L.toEntryId()) }
        }

    @Test
    fun `delegates path deletion to repository`() =
        runTest {
            assetDeleter.delete(
                DeleteAssetsCommand.AtPath(
                    path = AssetPath("/users/123"),
                    labels = mapOf("animal" to "cat").toAssetLabels(),
                ),
            )

            coVerify(exactly = 1) {
                assetRepository.deleteAllByPath(
                    path = AssetPath("/users/123"),
                    labels = mapOf("animal" to "cat"),
                )
            }
        }

    @Test
    fun `delegates recursive deletion to repository`() =
        runTest {
            assetDeleter.delete(
                DeleteAssetsCommand.Recursively(
                    path = AssetPath("/users/123"),
                    labels = mapOf("animal" to "cat").toAssetLabels(),
                ),
            )

            coVerify(exactly = 1) {
                assetRepository.deleteRecursivelyByPath(
                    path = AssetPath("/users/123"),
                    labels = mapOf("animal" to "cat"),
                )
            }
        }
}
