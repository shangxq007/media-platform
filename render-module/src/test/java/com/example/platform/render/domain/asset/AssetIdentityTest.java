package com.example.platform.render.domain.asset;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AssetIdentityTest {

    @Test
    void shouldCreateAssetIdentity() {
        AssetIdentity identity = new AssetIdentity("asset_123", "v7",
                "asset://asset_123?v=v7", "xmp://asset/asset_123/version/v7");

        assertEquals("asset_123", identity.assetId());
        assertEquals("v7", identity.assetVersion());
        assertEquals("asset://asset_123?v=v7", identity.entityRef());
        assertEquals("xmp://asset/asset_123/version/v7", identity.xmpUri());
    }

    @Test
    void shouldAllowNullFields() {
        AssetIdentity identity = new AssetIdentity("asset_456", null, null, null);

        assertEquals("asset_456", identity.assetId());
        assertNull(identity.assetVersion());
        assertNull(identity.entityRef());
        assertNull(identity.xmpUri());
    }
}
