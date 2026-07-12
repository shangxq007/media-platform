package com.example.platform.storage.delivery.diagnostics;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.storage.delivery.contract.StorageDeliveryProfileId;
import com.example.platform.storage.delivery.registry.StorageDeliveryProfileRegistry;
import org.junit.jupiter.api.Test;

class StorageDeliveryProfileDiagnosticsServiceTest {

    private final StorageDeliveryProfileRegistry registry = StorageDeliveryProfileRegistry.fromCanonicalCatalog();
    private final StorageDeliveryProfileDiagnosticsService service = new StorageDeliveryProfileDiagnosticsService(registry);

    @Test
    void testDiagnosticsSummary() {
        var response = service.getDiagnostics();

        assertEquals("READ_ONLY", response.diagnosticsMode());
        assertFalse(response.runtimeSwitchingImplemented());
        assertFalse(response.artifactAccessUsesRegistry());
        assertFalse(response.providerSelectionUsesRegistry());
        assertFalse(response.remoteCallsPerformed());
        assertEquals(8, response.profileCount());
        assertEquals(StorageDeliveryProfileId.PREVIEW_R2_SIGNED_URL, response.defaultProfileId());
    }

    @Test
    void testProfileDiagnostics() {
        var item = service.getProfileDiagnostics(StorageDeliveryProfileId.PREVIEW_R2_SIGNED_URL);

        assertTrue(item.isPresent());
        assertEquals(StorageDeliveryProfileId.PREVIEW_R2_SIGNED_URL, item.get().profileId());
        assertTrue(item.get().enabled());
        assertTrue(item.get().runtimeSelectable());
        assertTrue(item.get().capabilities().supportsSignedUrl());
        assertFalse(item.get().securityPolicy().signedUrlPersisted());
    }

    @Test
    void testUnknownProfileReturnsEmpty() {
        var item = service.getProfileDiagnostics(new StorageDeliveryProfileId("nonexistent"));
        assertTrue(item.isEmpty());
    }

    @Test
    void testValidationDiagnostics() {
        var validation = service.getValidationDiagnostics();
        assertNotNull(validation);
        assertNotNull(validation.valid());
    }

    @Test
    void testNoSensitiveFields() {
        var response = service.getDiagnostics();

        // Verify no bucket/objectKey/storageReferenceId/signedUrl
        String json = response.toString();
        assertFalse(json.contains("bucketName"));
        assertFalse(json.contains("objectKey"));
        assertFalse(json.contains("storageReferenceId"));
        assertFalse(json.contains("signedUrl"));
        assertFalse(json.contains("accessKey"));
        assertFalse(json.contains("secretKey"));
    }

    @Test
    void testNoRemoteCalls() {
        // Service works without credentials
        var response = service.getDiagnostics();
        assertFalse(response.remoteCallsPerformed());
    }
}
