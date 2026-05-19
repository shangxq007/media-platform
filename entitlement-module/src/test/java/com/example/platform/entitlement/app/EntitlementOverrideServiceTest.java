package com.example.platform.entitlement.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.entitlement.domain.EntitlementOverride;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

class EntitlementOverrideServiceTest {

    private EntitlementOverrideService service;

    @BeforeEach
    void setUp() {
        service = new EntitlementOverrideService(null, null);
    }

    @Test
    void createOverrideReturnsOverrideWithId() {
        EntitlementOverride result = service.createOverride(
                "TENANT", "tenant-1", "FEATURE_ADD",
                "{\"features\":[\"render.4k\"]}",
                Instant.now(), Instant.now().plusSeconds(86400), "admin");

        assertNotNull(result);
        assertNotNull(result.id());
        assertEquals("TENANT", result.subjectType());
        assertEquals("tenant-1", result.subjectId());
        assertEquals("FEATURE_ADD", result.overrideKind());
        assertEquals("ACTIVE", result.status());
    }

    @Test
    void queryOverridesReturnsEmptyListWithoutRepository() {
        List<EntitlementOverride> result = service.queryOverrides("tenant-1");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getOverrideReturnsEmptyWithoutRepository() {
        Optional<EntitlementOverride> result = service.getOverride("some-id");
        assertTrue(result.isEmpty());
    }

    @Test
    void updateOverrideThrowsWithoutRepository() {
        assertThrows(IllegalStateException.class, () ->
                service.updateOverride("id", "kind", "payload", Instant.now(), null, "admin"));
    }

    @Test
    void disableOverrideThrowsWithoutRepository() {
        assertThrows(IllegalStateException.class, () ->
                service.disableOverride("id", "admin"));
    }

    @Test
    void archiveOverrideThrowsWithoutRepository() {
        assertThrows(IllegalStateException.class, () ->
                service.archiveOverride("id", "admin"));
    }

    @Test
    void enableOverrideThrowsWithoutRepository() {
        assertThrows(IllegalStateException.class, () ->
                service.enableOverride("id", "admin"));
    }

    @Test
    void createOverrideWithNullExpiry() {
        EntitlementOverride result = service.createOverride(
                "TENANT", "tenant-1", "FEATURE_ADD",
                "{\"features\":[\"render.4k\"]}",
                Instant.now(), null, "admin");

        assertNotNull(result);
        assertNull(result.expiresAt());
    }
}
