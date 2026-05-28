package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.web.TenantContext;
import com.example.platform.shared.web.TenantGuard;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.mockito.Mockito.mock;

import java.util.List;

/**
 * Cross-tenant access control tests.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>TenantContext is isolated per-request</li>
 *   <li>TenantGuard rejects cross-tenant access</li>
 *   <li>No fallback to "tenant-1" when TenantContext is absent</li>
 * </ul>
 */
class CrossTenantAccessTest {

    @BeforeEach
    void setUp() {
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void tenantContextIsNullByDefault() {
        assertEquals(null, TenantContext.get(),
                "TenantContext should be null when not set");
    }

    @Test
    void tenantContextSetAndRetrieved() {
        TenantContext.set("tenant-a");
        assertEquals("tenant-a", TenantContext.get());
    }

    @Test
    void tenantContextClearedAfterUse() {
        TenantContext.set("tenant-a");
        assertEquals("tenant-a", TenantContext.get());
        TenantContext.clear();
        assertEquals(null, TenantContext.get(),
                "TenantContext should be null after clear()");
    }

    @Test
    void tenantGuardRequireTenantIdThrowsWhenNull() {
        TenantContext.clear();
        try {
            TenantGuard.requireTenantId();
            throw new AssertionError("Expected PlatformException");
        } catch (com.example.platform.shared.web.PlatformException ex) {
            assertTrue(ex.getErrorCode().code().contains("401"),
                    "Error should be 401 tenant required");
        }
    }

    @Test
    void tenantGuardRequireTenantIdReturnsWhenSet() {
        TenantContext.set("tenant-a");
        String tenantId = TenantGuard.requireTenantId();
        assertEquals("tenant-a", tenantId);
    }

    @Test
    void tenantGuardAssertSameTenantAllowsSameTenant() {
        TenantContext.set("tenant-a");
        TenantGuard.assertSameTenant("tenant-a");
    }

    @Test
    void tenantGuardAssertSameTenantRejectsDifferentTenant() {
        TenantContext.set("tenant-a");
        try {
            TenantGuard.assertSameTenant("tenant-b");
            throw new AssertionError("Expected PlatformException");
        } catch (com.example.platform.shared.web.PlatformException ex) {
            assertTrue(ex.getErrorCode().code().contains("403"),
                    "Error should be 403 access denied");
        }
    }

    @Test
    void tenantAGuestCannotAccessTenantBResource() {
        TenantContext.set("tenant-a");
        try {
            TenantGuard.assertSameTenant("tenant-b");
            throw new AssertionError("Cross-tenant access should be rejected");
        } catch (com.example.platform.shared.web.PlatformException ex) {
            // Expected: tenant-a user cannot access tenant-b resource
        }
    }

    @Test
    void tenantContextDoesNotFallbackToDefault() {
        TenantContext.clear();
        String tenantId = TenantContext.get();
        assertEquals(null, tenantId,
                "TenantContext must NOT fallback to 'tenant-1' or any default");
    }

    @Test
    void tenantGuardTenantOrDefaultUsesContextWhenExplicitIsNull() {
        TenantContext.set("tenant-a");
        String result = TenantGuard.tenantOrDefault(null);
        assertEquals("tenant-a", result,
                "Should use TenantContext when explicit tenantId is null");
    }

    @Test
    void tenantGuardTenantOrDefaultRejectsMismatchedExplicit() {
        TenantContext.set("tenant-a");
        try {
            TenantGuard.tenantOrDefault("tenant-b");
            throw new AssertionError("Should reject mismatched explicit tenantId");
        } catch (com.example.platform.shared.web.PlatformException ex) {
            // Expected
        }
    }

    @Test
    void tenantGuardSameTenantIfContextPresentSkipsWhenContextNull() {
        TenantContext.clear();
        TenantGuard.assertSameTenantIfContextPresent("any-tenant");
    }

    @Test
    void tenantGuardSameTenantIfContextPresentRejectsMismatch() {
        TenantContext.set("tenant-a");
        try {
            TenantGuard.assertSameTenantIfContextPresent("tenant-b");
            throw new AssertionError("Should reject mismatch when context is present");
        } catch (com.example.platform.shared.web.PlatformException ex) {
            // Expected
        }
    }

    @Test
    void tenantHeaderGuardFilterRejectsMismatchedHeader() throws Exception {
        OAuth2SecurityProperties props = new OAuth2SecurityProperties(
                true, "https://auth.example/", null, "tenantId", "roles", "platform_user_id",
                false, true, true, "tenant-1");
        TenantHeaderGuardFilter filter = new TenantHeaderGuardFilter(props);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.tenantId", "tenant-a");
        request.addHeader("X-Tenant-ID", "tenant-b");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus(),
                "Should return 403 when X-Tenant-ID does not match JWT tenant");
    }

    @Test
    void tenantHeaderGuardFilterIgnoresMissingHeader() throws Exception {
        OAuth2SecurityProperties props = new OAuth2SecurityProperties(
                true, "https://auth.example/", null, "tenantId", "roles", "platform_user_id",
                false, true, true, "tenant-1");
        TenantHeaderGuardFilter filter = new TenantHeaderGuardFilter(props);

        jakarta.servlet.http.HttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest();
        request.setAttribute("jwt.tenantId", "tenant-a");
        // No X-Tenant-ID header set

        org.springframework.mock.web.MockHttpServletResponse response =
                new org.springframework.mock.web.MockHttpServletResponse();
        jakarta.servlet.FilterChain chain = org.mockito.Mockito.mock(jakarta.servlet.FilterChain.class);

        filter.doFilter(request, response, chain);

        org.mockito.Mockito.verify(chain).doFilter(request, response);
    }
}
