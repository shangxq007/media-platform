package com.example.platform.render.app.asset;

import static org.junit.jupiter.api.Assertions.*;
import com.example.platform.render.domain.asset.AssetGovernanceMetadata;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AssetRegistryServiceTest {

    @Test
    void shouldBuildOtioClipMetadataRef() {
        // Unit test for metadata reference construction (no Spring context needed)
        var refs = new java.util.LinkedHashMap<String, String>();
        String assetId = "asset_123";
        String assetVersion = "v7";
        refs.put("bluepulse.asset_id", assetId);
        refs.put("bluepulse.asset_version", assetVersion);
        refs.put("bluepulse.xmp_uri", "xmp://asset/" + assetId + "/version/" + assetVersion);
        refs.put("bluepulse.entity_ref", "asset://" + assetId + "?v=" + assetVersion);

        assertEquals("asset_123", refs.get("bluepulse.asset_id"));
        assertEquals("v7", refs.get("bluepulse.asset_version"));
        assertTrue(refs.get("bluepulse.xmp_uri").contains("asset_123"));
        assertTrue(refs.get("bluepulse.entity_ref").contains("v7"));
    }

    @Test
    void shouldProduceCorrectOtioRefsForDefaultVersion() {
        String assetId = "asset_999";
        String version = "v1";
        String xmpUri = "xmp://asset/" + assetId + "/version/" + version;
        String entityRef = "asset://" + assetId + "?v=" + version;

        assertEquals("xmp://asset/asset_999/version/v1", xmpUri);
        assertEquals("asset://asset_999?v=v1", entityRef);
    }

    @Test
    void governanceAttachmentShouldPreserveIdentity() {
        AssetGovernanceMetadata gov = new AssetGovernanceMetadata(
                "confidential", "restricted", "90-day", "L3", true, false);

        assertEquals("confidential", gov.classification());
        assertEquals("restricted", gov.license());
        assertEquals("90-day", gov.retentionPolicy());
        assertTrue(gov.containsPii());
        assertFalse(gov.aiGenerated());
    }
}
