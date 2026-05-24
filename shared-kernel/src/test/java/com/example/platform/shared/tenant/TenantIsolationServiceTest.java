package com.example.platform.shared.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TenantIsolationServiceTest {

    private TenantIsolationService service;

    @BeforeEach
    void setUp() {
        service = new TenantIsolationService();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void requireTenantIdReturnsSetTenant() {
        TenantContext.set("tenant-1");
        assertEquals("tenant-1", service.requireTenantId());
    }

    @Test
    void requireTenantIdThrowsWhenNotSet() {
        TenantContext.clear();
        assertThrows(SecurityException.class, () -> service.requireTenantId());
    }

    @Test
    void requireTenantIdThrowsWhenBlank() {
        TenantContext.set("  ");
        assertThrows(SecurityException.class, () -> service.requireTenantId());
    }

    @Test
    void assertTenantAccessPassesForSameTenant() {
        TenantContext.set("tenant-1");
        service.assertTenantAccess("tenant-1");
    }

    @Test
    void assertTenantAccessThrowsForDifferentTenant() {
        TenantContext.set("tenant-1");
        assertThrows(SecurityException.class, () ->
                service.assertTenantAccess("tenant-2"));
    }

    @Test
    void isTenantSetReturnsTrueWhenSet() {
        TenantContext.set("tenant-1");
        assertTrue(service.isTenantSet());
    }

    @Test
    void isTenantSetReturnsFalseWhenNotSet() {
        TenantContext.clear();
        assertFalse(service.isTenantSet());
    }
}
