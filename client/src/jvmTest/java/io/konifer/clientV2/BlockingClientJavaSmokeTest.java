package io.konifer.clientV2;

import io.konifer.client.asset.info.MetadataUtilsKt;
import io.konifer.clientV2.assets.BlockingAbsoluteAssetSelection;
import io.konifer.clientV2.assets.BlockingAssetAtPath;
import io.konifer.clientV2.assets.BlockingAssetSelection;
import io.konifer.clientV2.assets.BlockingRelativeAssetSelection;
import io.konifer.clientV2.assets.BlockingVariantSelection;
import io.konifer.clientV2.assets.fetch.RequestedTransformation;
import io.konifer.common.image.ImageFormat;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class BlockingClientJavaSmokeTest {
    @Test
    void javaCanUseBlockingSelectionsFromTheAssetsPackage() {
        try (KoniferBlockingClientV2 client = KoniferBlockingClientV2.build("http://localhost:8080")) {
            BlockingAssetAtPath assets = client.assets("images/example");
            BlockingAssetSelection selection = assets;
            BlockingRelativeAssetSelection filtered = assets.matchingLabels(Map.of("category", "avatar"));
            BlockingAbsoluteAssetSelection entry = assets.entry(42);
            BlockingVariantSelection variant = entry.originalVariant();
            assertNotNull(selection);
            assertNotNull(filtered);
            assertNotNull(variant);
        }
    }

    @Test
    void javaCanConfigureHttpTimeouts() {
        KoniferHttpConfiguration configuration = new KoniferHttpConfiguration.Builder()
                .requestTimeoutMillis(30_000L)
                .connectTimeoutMillis(5_000L)
                .socketTimeoutMillis(Long.MAX_VALUE)
                .build();
        try (KoniferBlockingClientV2 client = KoniferBlockingClientV2.build(
                "http://localhost:8080", null, HmacSigningAlgorithm.HMAC_SHA256, configuration)) {
            assertNotNull(client.assets("images/example"));
        }
    }

    @Test
    void javaCanBuildAndComposeBlockingRequests() {
        try (KoniferBlockingClientV2 client = KoniferBlockingClientV2.build("http://localhost:8080")) {
            assertNotNull(client.assets("images/example").originalVariant());
            assertNotNull(client.assets("images/example").variant(
                    new RequestedTransformation.Builder().width(100).build()));
            assertNotNull(client.assets("images/example").newAsset()
                    .fromInputStream(() -> new ByteArrayInputStream(new byte[]{1, 2, 3}), ImageFormat.PNG));
            assertNotNull(client.assets("images/example").updateAsset(MetadataUtilsKt.createInfoResponse())
                    .withLabel("category", "avatar")
                    .withoutTag("draft"));
            assertNotNull(client.ruleEvaluation().fromBytes(new byte[]{1, 2, 3}, ImageFormat.PNG));
        }
    }
}
