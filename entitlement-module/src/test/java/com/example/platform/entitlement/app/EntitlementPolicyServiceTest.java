package com.example.platform.entitlement.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.platform.entitlement.infrastructure.TenantTierJdbcRepository;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EntitlementPolicyServiceTest {

    @Test
    void missingTierIsPresentationMetadataOnly() {
        EntitlementPolicyService service = new EntitlementPolicyService(Optional.empty());
        assertEquals("FREE", service.getTier("unknown-tenant"));
    }

    @Test
    void persistsBeforeUpdatingDerivedMetadataProjection() {
        TenantTierJdbcRepository repository = mock(TenantTierJdbcRepository.class);
        EntitlementPolicyService service = new EntitlementPolicyService(Optional.of(repository));

        service.setTier("tenant-1", "pro");

        verify(repository).upsert("tenant-1", "PRO");
        assertEquals("PRO", service.getTier("tenant-1"));
    }

    @Test
    void persistenceFailureCannotChangeTheProjectionOrGrantAnything() {
        TenantTierJdbcRepository repository = mock(TenantTierJdbcRepository.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(repository).upsert("tenant-1", "TEAM");
        EntitlementPolicyService service = new EntitlementPolicyService(Optional.of(repository));

        assertThrows(IllegalStateException.class, () -> service.setTier("tenant-1", "TEAM"));
        assertEquals("FREE", service.getTier("tenant-1"));
    }

    @Test
    void exposesNoCapabilityDecisionApi() {
        var names = Arrays.stream(EntitlementPolicyService.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName).toList();
        for (String retired : java.util.List.of(
                "validateExport", "isFeatureEnabled", "getFeatureFlags",
                "getExportCapabilities", "getProviderAccess")) {
            assertFalse(names.contains(retired));
        }
    }
}
