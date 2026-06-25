package com.example.platform.render.domain.asset;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AssetGovernanceMetadataTest {

    @Test
    void shouldCreateGovernanceMetadata() {
        AssetGovernanceMetadata gov = new AssetGovernanceMetadata(
                "internal", "enterprise-owned", "30-day", "L2", true, true);

        assertEquals("internal", gov.classification());
        assertEquals("enterprise-owned", gov.license());
        assertEquals("30-day", gov.retentionPolicy());
        assertEquals("L2", gov.securityLevel());
        assertTrue(gov.containsPii());
        assertTrue(gov.aiGenerated());
    }

    @Test
    void defaultsShouldBeSafe() {
        AssetGovernanceMetadata gov = AssetGovernanceMetadata.defaults();

        assertNull(gov.classification());
        assertNull(gov.license());
        assertNull(gov.retentionPolicy());
        assertNull(gov.securityLevel());
        assertFalse(gov.containsPii());
        assertFalse(gov.aiGenerated());
    }
}
