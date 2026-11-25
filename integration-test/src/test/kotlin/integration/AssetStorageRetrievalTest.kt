package integration

import io.konifer.client.KoniferResult
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.UUID

class AssetStorageRetrievalTest : BaseIntegrationTest() {
    companion object {
        @JvmStatic
        fun metadataSource() =
            listOf(
                arguments(named("only alt", "an alt"), emptyMap<String, String>(), emptySet<String>()),
                arguments(named("all fields", "an alt"), mapOf("key1" to "key2"), setOf("tag1", "tag2")),
                arguments(named("no alt", null), mapOf("key1" to "key2"), setOf("tag1", "tag2")),
                arguments(named("only tags", null), emptyMap<String, String>(), setOf("tag1", "tag2")),
                arguments(named("only labels", null), mapOf("key1" to "key2"), emptySet<String>()),
                arguments(named("no fields", null), emptyMap<String, String>(), emptySet<String>()),
                arguments(named("all with multiple labels", "an alt"), mapOf("key1" to "key2", "key3" to "key4"), setOf("tag1", "tag2")),
            )
    }

    @ParameterizedTest
    @MethodSource("metadataSource")
    fun `can store and retrieve asset regardless of supplied metadata`(
        alt: String?,
        labels: Map<String, String>,
        tags: Set<String>,
    ) = runBlocking<Unit> {
        val path = UUID.randomUUID().toString()
        val (image, attributes) = ImageFactory.testImage()
        var builder =
            client
                .assets(path)
                .newAsset()
                .fromBytes(
                    bytes = image,
                    format = attributes.format,
                )
        builder = alt?.let(builder::withAlt) ?: builder

        val storeResponse =
            builder
                .withLabels(labels)
                .withTags(tags)
                .store()
        storeResponse::class shouldBe KoniferResult.Success::class

        // Info
        val infoResponse =
            client
                .assets(path)
                .fetchInfo()

        infoResponse::class shouldBe KoniferResult.Success::class

        with(infoResponse.valueOrThrow()) {
            this.labels shouldContainExactly labels
            this.tags shouldContainExactlyInAnyOrder tags
            this.alt shouldBe alt
        }

        // Entries
        val entriesResponse =
            client
                .assets(path)
                .fetchEntries()

        entriesResponse::class shouldBe KoniferResult.Success::class

        val entry = entriesResponse.valueOrThrow().entries shouldHaveSize 1

        with(entry.single()) {
            this.labels shouldContainExactly labels
            this.tags shouldContainExactlyInAnyOrder tags
            this.alt shouldBe alt
        }
    }
}
