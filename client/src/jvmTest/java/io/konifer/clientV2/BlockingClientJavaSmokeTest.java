package io.konifer.clientV2;

import io.konifer.client.asset.info.MetadataUtilsKt;
import io.konifer.clientV2.assets.fetch.RequestedTransformation;
import io.konifer.common.image.ImageFormat;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class BlockingClientJavaSmokeTest {
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
