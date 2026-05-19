package com.example.platform.entitlement.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.entitlement.domain.EntitlementBundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class EntitlementBundleServiceTest {

    private EntitlementBundleService service;

    @BeforeEach
    void setUp() {
        service = new EntitlementBundleService(null, null);
    }

    @Test
    void createBundleReturnsBundleWithId() {
        EntitlementBundle result = service.createBundle(
                "pro-bundle", "Pro Bundle", "Professional features",
                false, false, true, 5, 3, 300,
                10737418240L, false, false,
                false, 1000, true, true, true, "admin");

        assertNotNull(result);
        assertNotNull(result.id());
        assertEquals("pro-bundle", result.bundleKey());
        assertEquals("Pro Bundle", result.name());
        assertEquals("ACTIVE", result.status());
        assertTrue(result.customFontsAllowed());
        assertFalse(result.gpuAllowed());
    }

    @Test
    void getBundleReturnsEmptyWithoutRepository() {
        Optional<EntitlementBundle> result = service.getBundle("nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    void listBundlesReturnsEmptyWithoutRepository() {
        List<EntitlementBundle> result = service.listBundles();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void updateBundleThrowsWithoutRepository() {
        assertThrows(IllegalStateException.class, () ->
                service.updateBundle("key", "name", "desc",
                        false, false, false, 0, 0, 0,
                        0, false, false, false, 0,
                        false, false, false, "admin"));
    }

    @Test
    void archiveBundleThrowsWithoutRepository() {
        assertThrows(IllegalStateException.class, () ->
                service.archiveBundle("key", "admin"));
    }

    @Test
    void createEnterpriseBundle() {
        EntitlementBundle result = service.createBundle(
                "enterprise-bundle", "Enterprise", "Enterprise features",
                true, true, true, 20, 50, 6000,
                1099511627776L, false, true,
                true, 999999, true, true, true, "admin");

        assertNotNull(result);
        assertTrue(result.gpuAllowed());
        assertTrue(result.remoteWorkerAllowed());
        assertTrue(result.priorityQueueAllowed());
        assertTrue(result.betaEffectsAllowed());
        assertEquals(50, result.maxConcurrentJobs());
    }
}
