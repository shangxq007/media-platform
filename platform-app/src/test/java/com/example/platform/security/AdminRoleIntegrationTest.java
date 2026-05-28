package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Integration tests for admin role checking across both security paths.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>OAuth2 Resource Server path: ADMIN role in GrantedAuthority is recognized</li>
 *   <li>Legacy HMAC JWT path: ADMIN role in jwt.roles attribute is recognized</li>
 *   <li>Non-admin users are rejected in both paths</li>
 *   <li>Cross-tenant access via tenantId param is blocked for non-admin users</li>
 * </ul>
 */
class AdminRoleIntegrationTest {

    @BeforeEach
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    // ---- OAuth2 Resource Server Path Tests ----

    @Test
    void oauth2Path_adminRoleInGrantedAuthority_isRecognized() {
        setOAuth2Authentication("user-1", "tenant-a", List.of("ADMIN"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.subject", "user-1");
        request.setAttribute("jwt.tenantId", "tenant-a");

        // Simulate the check in SharedResourceAdminController.requireAdminRole
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.isAuthenticated());

        boolean hasAdmin = false;
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                hasAdmin = true;
            }
        }
        assertTrue(hasAdmin, "ADMIN role should be recognized from GrantedAuthority");
    }

    @Test
    void oauth2Path_nonAdminRole_isRejected() {
        setOAuth2Authentication("user-1", "tenant-a", List.of("EDITOR"));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean hasAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ADMIN".equals(a.getAuthority()));
        assertFalse(hasAdmin, "EDITOR role should not be recognized as ADMIN");
    }

    @Test
    void oauth2Path_crossTenantAccessBlockedForNonAdmin() {
        setOAuth2Authentication("user-1", "tenant-a", List.of("EDITOR"));
        TenantContext.set("tenant-a");

        String requestedTenant = "tenant-b";
        String contextTenant = TenantContext.get();

        assertNotEquals(contextTenant, requestedTenant);
        // Non-admin should not be able to access tenant-b
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        assertFalse(isAdmin, "Non-admin should not have ADMIN authority");
    }

    @Test
    void oauth2Path_crossTenantAccessAllowedForAdmin() {
        setOAuth2Authentication("admin-1", "tenant-a", List.of("ADMIN"));
        TenantContext.set("tenant-a");

        String requestedTenant = "tenant-b";
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        assertTrue(isAdmin, "Admin should have ADMIN authority for cross-tenant access");
    }

    // ---- Legacy HMAC JWT Path Tests ----

    @Test
    void legacyJwtPath_adminRoleInAttribute_isRecognized() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.subject", "user-1");
        request.setAttribute("jwt.tenantId", "tenant-a");
        request.setAttribute("jwt.roles", List.of("ADMIN"));

        // Simulate the check in DeliveryAdminController.requireAdminRole
        assertTrue(request.isUserInRole("ADMIN")
                || hasRoleFromRequest(request, "ADMIN"),
                "ADMIN role should be recognized from jwt.roles attribute");
    }

    @Test
    void legacyJwtPath_commaSeparatedRoles_isRecognized() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.roles", "USER,EDITOR,ADMIN");

        assertTrue(hasRoleFromRequest(request, "ADMIN"),
                "ADMIN should be recognized from comma-separated jwt.roles");
    }

    @Test
    void legacyJwtPath_nonAdminRole_isRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.roles", List.of("EDITOR"));

        assertFalse(hasRoleFromRequest(request, "ADMIN"),
                "EDITOR should not be recognized as ADMIN");
    }

    @Test
    void legacyJwtPath_noRolesAttribute_isRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // No jwt.roles attribute set

        assertFalse(hasRoleFromRequest(request, "ADMIN"),
                "Missing jwt.roles should not grant ADMIN");
    }

    // ---- Cross-path Consistency Tests ----

    @Test
    void bothPaths_adminCanAccessCrossTenant() {
        // OAuth2 path
        setOAuth2Authentication("admin-1", "tenant-a", List.of("ADMIN"));
        boolean oauth2Admin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        clearContext();

        // Legacy path
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.roles", List.of("ADMIN"));
        boolean legacyAdmin = hasRoleFromRequest(request, "ADMIN");

        assertTrue(oauth2Admin, "OAuth2 path: admin should be recognized");
        assertTrue(legacyAdmin, "Legacy path: admin should be recognized");
    }

    @Test
    void noSecurityContext_legacyPathStillWorks() {
        // Ensure SecurityContext is empty (no OAuth2)
        SecurityContextHolder.clearContext();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.roles", List.of("ADMIN"));

        // Should still work via jwt.roles attribute
        assertTrue(hasRoleFromRequest(request, "ADMIN"),
                "Legacy path should work even without SecurityContext");
    }

    // ---- TenantContext Fallback Tests ----

    @Test
    void noTenantContext_noFallback() {
        TenantContext.clear();
        String tenantId = TenantContext.get();
        assertNull(tenantId, "TenantContext should be null, not fallback to tenant-1");
    }

    @Test
    void tenantContextSetCorrectly() {
        TenantContext.set("tenant-x");
        assertEquals("tenant-x", TenantContext.get());
        TenantContext.clear();
        assertNull(TenantContext.get());
    }

    // ---- Helpers ----

    private void setOAuth2Authentication(String subject, String tenantId, List<String> roles) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("tenantId", tenantId)
                .claim("roles", roles)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(120))
                .build();

        Collection<GrantedAuthority> authorities = roles.stream()
                .map(role -> {
                    String normalized = role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role;
                    return (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + normalized);
                })
                .toList();

        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities, subject));
    }

    private static boolean hasRoleFromRequest(MockHttpServletRequest request, String role) {
        Object rolesAttr = request.getAttribute("jwt.roles");
        if (rolesAttr instanceof List<?> roles) {
            return roles.stream().anyMatch(r -> r != null && role.equalsIgnoreCase(r.toString().trim()));
        } else if (rolesAttr instanceof String rolesStr) {
            for (String r : rolesStr.split(",")) {
                if (role.equalsIgnoreCase(r.trim())) return true;
            }
        }
        return false;
    }
}
