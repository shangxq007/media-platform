package com.example.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestEntitlementGrantSupportTest {

    @Test
    void longInputGrantIdIsBoundedCollisionResistantAndReplayStable() {
        String tenantId = "tenant-租户-🌐".repeat(40);
        String entitlementKey = "render.job.create/extended:权限".repeat(40);

        String first = TestEntitlementGrantSupport.grantId(tenantId, entitlementKey);
        String replay = TestEntitlementGrantSupport.grantId(tenantId, entitlementKey);

        assertEquals(first, replay);
        assertEquals(54, first.length());
        assertTrue(first.startsWith("test-grant-"));
        assertNotEquals(first, TestEntitlementGrantSupport.grantId(tenantId, entitlementKey + "-other"));
        assertNotEquals(
                TestEntitlementGrantSupport.grantId("tenant-a", "bc"),
                TestEntitlementGrantSupport.grantId("tenant-ab", "c"),
                "length framing must keep adjacent input pairs distinct");
    }
}
