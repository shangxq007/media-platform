package com.example.platform.web.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.example.platform.identity.app.PermissionService;
import com.example.platform.identity.app.RoleService;
import com.example.platform.shared.entitlement.EntitlementPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NavigationDecisionServiceReasonCodeTest {

    private NavigationDecisionService service;
    private NavigationRegistryService registry;

    @BeforeEach
    void setUp() {
        registry = new NavigationRegistryService(new ObjectMapper());
        service = new NavigationDecisionService(
                registry, mock(PermissionService.class), mock(RoleService.class), mock(EntitlementPort.class), null);
    }

    @Test
    void hiddenRouteWithRoleMismatchUsesNav403NotGeneric404() {
        registry.saveRoute(new FrontendRouteDefinition(
                "secret-admin", "/secret", "SecretPage", "Secret",
                null, "admin", null, 10,
                null, List.of(), List.of("ADMIN"), List.of(),
                null, List.of(), List.of(),
                true, true, null, null,
                List.of(), List.of(), null, null));

        var decision = service.evaluateRoutes(
                        "user-1", "tenant-1", "WEB", "ENTERPRISE",
                        Set.of("USER"), Set.of(), Set.of())
                .stream()
                .filter(d -> "secret-admin".equals(d.routeKey()))
                .findFirst()
                .orElseThrow();

        assertFalse(decision.visible());
        assertEquals("NAV-403-ROLE", decision.reasonCode());
    }

    @Test
    void meDashboardRegisteredInCanonicalRoutes() {
        assertTrue(registry.getRouteByKey("me-dashboard").isPresent());
        assertTrue(registry.getRouteByKey("me-billing").isPresent());
    }
}
