package com.example.platform.web.navigation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.identity.app.PermissionService;
import com.example.platform.identity.app.RoleService;
import com.example.platform.policy.api.FeatureFlagEvaluator;
import com.example.platform.shared.entitlement.EntitlementPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

class NavigationDecisionServiceFeatureFlagTest {

    private NavigationDecisionService service;
    private NavigationRegistryService registryService;
    private PermissionService permissionService;
    private RoleService roleService;
    private EntitlementPort entitlementPort;
    private FeatureFlagEvaluator featureFlagEvaluator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        registryService = new NavigationRegistryService(objectMapper);
        permissionService = mock(PermissionService.class);
        roleService = mock(RoleService.class);
        entitlementPort = mock(EntitlementPort.class);
        featureFlagEvaluator = mock(FeatureFlagEvaluator.class);
        service = new NavigationDecisionService(
                registryService, permissionService, roleService,
                entitlementPort, featureFlagEvaluator);
    }

    @Test
    void evaluateRouteWithRequiredFeatureFlagEnabled() {
        FrontendRouteDefinition route = new FrontendRouteDefinition(
                "test-route", "/test", "TestPage", "Test",
                "desc", "main", "icon", 10,
                null, List.of(), List.of(), List.of(),
                null, List.of(), List.of(),
                true, true, null, null,
                List.of(), List.of("export.gpu.v2.enabled"),
                null, null);
        registryService.saveRoute(route);

        when(featureFlagEvaluator.isEnabled("export.gpu.v2.enabled", "user-1",
                Map.of("tenantId", "tenant-1"), false)).thenReturn(true);

        List<NavigationDecisionService.RouteVisibilityDecision> decisions = service.evaluateRoutes(
                "user-1", "tenant-1", "WEB", "ENTERPRISE",
                Set.of("ADMIN"), Set.of("config:read"), Set.of());

        NavigationDecisionService.RouteVisibilityDecision decision = decisions.stream()
                .filter(d -> "test-route".equals(d.routeKey()))
                .findFirst().orElse(null);

        assertNotNull(decision);
        assertTrue(decision.visible());
        assertTrue(decision.enabled());
        assertFalse(decision.disabledByFeatureFlag());
        assertNotNull(decision.matchedFeatureFlags());
        assertTrue(decision.matchedFeatureFlags().get("export.gpu.v2.enabled"));
    }

    @Test
    void evaluateRouteWithRequiredFeatureFlagDisabled() {
        FrontendRouteDefinition route = new FrontendRouteDefinition(
                "test-route-ff", "/test-ff", "TestPageFF", "TestFF",
                "desc", "main", "icon", 10,
                null, List.of(), List.of(), List.of(),
                null, List.of(), List.of(),
                true, true, null, null,
                List.of(), List.of("export.gpu.v2.enabled"),
                null, null);
        registryService.saveRoute(route);

        when(featureFlagEvaluator.isEnabled("export.gpu.v2.enabled", "user-1",
                Map.of("tenantId", "tenant-1"), false)).thenReturn(false);

        List<NavigationDecisionService.RouteVisibilityDecision> decisions = service.evaluateRoutes(
                "user-1", "tenant-1", "WEB", "ENTERPRISE",
                Set.of("ADMIN"), Set.of("config:read"), Set.of());

        NavigationDecisionService.RouteVisibilityDecision decision = decisions.stream()
                .filter(d -> "test-route-ff".equals(d.routeKey()))
                .findFirst().orElse(null);

        assertNotNull(decision);
        assertTrue(decision.visible());
        assertFalse(decision.enabled());
        assertTrue(decision.disabledByFeatureFlag());
        assertNotNull(decision.matchedFeatureFlags());
        assertFalse(decision.matchedFeatureFlags().get("export.gpu.v2.enabled"));
    }

    @Test
    void evaluateRouteWithBetaFlagEnabled() {
        FrontendRouteDefinition route = new FrontendRouteDefinition(
                "beta-route", "/beta", "BetaPage", "Beta",
                "desc", "main", "icon", 10,
                null, List.of(), List.of(), List.of(),
                null, List.of(), List.of(),
                true, true, null, null,
                List.of(), List.of(),
                "beta.flag.key", null);
        registryService.saveRoute(route);

        when(featureFlagEvaluator.isEnabled("beta.flag.key", "user-1",
                Map.of("tenantId", "tenant-1"), false)).thenReturn(true);

        List<NavigationDecisionService.RouteVisibilityDecision> decisions = service.evaluateRoutes(
                "user-1", "tenant-1", "WEB", "ENTERPRISE",
                Set.of("ADMIN"), Set.of(), Set.of());

        NavigationDecisionService.RouteVisibilityDecision decision = decisions.stream()
                .filter(d -> "beta-route".equals(d.routeKey()))
                .findFirst().orElse(null);

        assertNotNull(decision);
        assertTrue(decision.visible());
        assertTrue(decision.enabled());
        assertTrue(decision.beta());
    }

    @Test
    void evaluateRouteWithBetaFlagDisabled() {
        FrontendRouteDefinition route = new FrontendRouteDefinition(
                "beta-route-hidden", "/beta-hidden", "BetaPageHidden", "BetaHidden",
                "desc", "main", "icon", 10,
                null, List.of(), List.of(), List.of(),
                null, List.of(), List.of(),
                true, true, null, null,
                List.of(), List.of(),
                "beta.flag.key", null);
        registryService.saveRoute(route);

        when(featureFlagEvaluator.isEnabled("beta.flag.key", "user-1",
                Map.of("tenantId", "tenant-1"), false)).thenReturn(false);

        List<NavigationDecisionService.RouteVisibilityDecision> decisions = service.evaluateRoutes(
                "user-1", "tenant-1", "WEB", "ENTERPRISE",
                Set.of("ADMIN"), Set.of(), Set.of());

        NavigationDecisionService.RouteVisibilityDecision decision = decisions.stream()
                .filter(d -> "beta-route-hidden".equals(d.routeKey()))
                .findFirst().orElse(null);

        assertNotNull(decision);
        assertFalse(decision.visible());
        assertTrue(decision.enabled());
        assertFalse(decision.beta());
    }

    @Test
    void evaluateRouteWithRolloutFlagEnabled() {
        FrontendRouteDefinition route = new FrontendRouteDefinition(
                "rollout-route", "/rollout", "RolloutPage", "Rollout",
                "desc", "main", "icon", 10,
                null, List.of(), List.of(), List.of(),
                null, List.of(), List.of(),
                true, true, null, null,
                List.of(), List.of(),
                null, "rollout.flag.key");
        registryService.saveRoute(route);

        when(featureFlagEvaluator.isEnabled("rollout.flag.key", "user-1",
                Map.of("tenantId", "tenant-1"), false)).thenReturn(true);

        List<NavigationDecisionService.RouteVisibilityDecision> decisions = service.evaluateRoutes(
                "user-1", "tenant-1", "WEB", "ENTERPRISE",
                Set.of("ADMIN"), Set.of(), Set.of());

        NavigationDecisionService.RouteVisibilityDecision decision = decisions.stream()
                .filter(d -> "rollout-route".equals(d.routeKey()))
                .findFirst().orElse(null);

        assertNotNull(decision);
        assertTrue(decision.visible());
        assertTrue(decision.enabled());
        assertTrue(decision.rollout());
    }

    @Test
    void evaluateRouteWithRolloutFlagDisabled() {
        FrontendRouteDefinition route = new FrontendRouteDefinition(
                "rollout-route-disabled", "/rollout-disabled", "RolloutPageDisabled", "RolloutDisabled",
                "desc", "main", "icon", 10,
                null, List.of(), List.of(), List.of(),
                null, List.of(), List.of(),
                true, true, null, null,
                List.of(), List.of(),
                null, "rollout.flag.key");
        registryService.saveRoute(route);

        when(featureFlagEvaluator.isEnabled("rollout.flag.key", "user-1",
                Map.of("tenantId", "tenant-1"), false)).thenReturn(false);

        List<NavigationDecisionService.RouteVisibilityDecision> decisions = service.evaluateRoutes(
                "user-1", "tenant-1", "WEB", "ENTERPRISE",
                Set.of("ADMIN"), Set.of(), Set.of());

        NavigationDecisionService.RouteVisibilityDecision decision = decisions.stream()
                .filter(d -> "rollout-route-disabled".equals(d.routeKey()))
                .findFirst().orElse(null);

        assertNotNull(decision);
        assertTrue(decision.visible());
        assertFalse(decision.enabled());
        assertTrue(decision.disabledByFeatureFlag());
        assertFalse(decision.rollout());
    }

    @Test
    void evaluateRouteWithNoFeatureFlagsUnaffected() {
        List<NavigationDecisionService.RouteVisibilityDecision> decisions = service.evaluateRoutes(
                "user-1", "tenant-1", "WEB", "ENTERPRISE",
                Set.of("ADMIN"), Set.of("config:read"), Set.of());

        NavigationDecisionService.RouteVisibilityDecision decision = decisions.stream()
                .filter(d -> "editor".equals(d.routeKey()))
                .findFirst().orElse(null);

        assertNotNull(decision);
        assertTrue(decision.visible());
        assertTrue(decision.enabled());
        assertFalse(decision.disabledByFeatureFlag());
        assertNotNull(decision.matchedFeatureFlags());
        assertTrue(decision.matchedFeatureFlags().isEmpty());
    }

    @Test
    void evaluateRouteWithMultipleFeatureFlags() {
        FrontendRouteDefinition route = new FrontendRouteDefinition(
                "multi-ff-route", "/multi", "MultiPage", "Multi",
                "desc", "main", "icon", 10,
                null, List.of(), List.of(), List.of(),
                null, List.of(), List.of(),
                true, true, null, null,
                List.of(), List.of("flag1", "flag2"),
                null, null);
        registryService.saveRoute(route);

        when(featureFlagEvaluator.isEnabled("flag1", "user-1",
                Map.of("tenantId", "tenant-1"), false)).thenReturn(true);
        when(featureFlagEvaluator.isEnabled("flag2", "user-1",
                Map.of("tenantId", "tenant-1"), false)).thenReturn(false);

        List<NavigationDecisionService.RouteVisibilityDecision> decisions = service.evaluateRoutes(
                "user-1", "tenant-1", "WEB", "ENTERPRISE",
                Set.of("ADMIN"), Set.of(), Set.of());

        NavigationDecisionService.RouteVisibilityDecision decision = decisions.stream()
                .filter(d -> "multi-ff-route".equals(d.routeKey()))
                .findFirst().orElse(null);

        assertNotNull(decision);
        assertFalse(decision.enabled());
        assertTrue(decision.disabledByFeatureFlag());
        assertEquals(2, decision.matchedFeatureFlags().size());
        assertTrue(decision.matchedFeatureFlags().get("flag1"));
        assertFalse(decision.matchedFeatureFlags().get("flag2"));
    }

    @Test
    void routeVisibilityDecisionBackwardCompatibility() {
        NavigationDecisionService.RouteVisibilityDecision decision =
                new NavigationDecisionService.RouteVisibilityDecision(
                        "key", "/path", "Title", "group", 10,
                        true, true, null, null,
                        null, null, null,
                        null, null,
                        Map.of(), false, false, false);

        assertEquals("key", decision.routeKey());
        assertTrue(decision.visible());
        assertTrue(decision.enabled());
        assertNotNull(decision.matchedFeatureFlags());
        assertFalse(decision.disabledByFeatureFlag());
    }

    @Test
    void frontendRouteDefinitionBackwardCompatibility() {
        FrontendRouteDefinition def = new FrontendRouteDefinition(
                "route", "/path", "Page", "Title",
                "desc", "main", "icon", 10,
                null, List.of(), List.of(), List.of(),
                null, List.of(), List.of(),
                true, true, null, null,
                List.of());

        assertNotNull(def.requiredFeatureFlags());
        assertTrue(def.requiredFeatureFlags().isEmpty());
        assertNull(def.betaFlagKey());
        assertNull(def.rolloutFlagKey());
    }

    @Test
    void frontendRouteDefinitionWithFeatureFlags() {
        FrontendRouteDefinition def = new FrontendRouteDefinition(
                "route", "/path", "Page", "Title",
                "desc", "main", "icon", 10,
                null, List.of(), List.of(), List.of(),
                null, List.of(), List.of(),
                true, true, null, null,
                List.of(), List.of("flag1"),
                "beta.key", "rollout.key");

        assertEquals(List.of("flag1"), def.requiredFeatureFlags());
        assertEquals("beta.key", def.betaFlagKey());
        assertEquals("rollout.key", def.rolloutFlagKey());
    }

    @Test
    void buildProfileIncludesFeatureFlagDecisions() {
        FrontendRouteDefinition route = new FrontendRouteDefinition(
                "profile-test", "/profile", "ProfilePage", "Profile",
                "desc", "main", "icon", 10,
                null, List.of(), List.of(), List.of(),
                null, List.of(), List.of(),
                true, true, null, null,
                List.of(), List.of("flag1"),
                null, null);
        registryService.saveRoute(route);

        when(featureFlagEvaluator.isEnabled("flag1", "user-1",
                Map.of("tenantId", "tenant-1"), false)).thenReturn(true);

        NavigationDecisionService.NavigationProfile profile = service.buildProfile(
                "user-1", "tenant-1", "WEB", "ENTERPRISE",
                Set.of(), Set.of(), Set.of());

        assertNotNull(profile);
        assertFalse(profile.routes().isEmpty());

        NavigationDecisionService.RouteVisibilityDecision decision = profile.routes().stream()
                .filter(d -> "profile-test".equals(d.routeKey()))
                .findFirst().orElse(null);

        assertNotNull(decision);
        assertTrue(decision.enabled());
        assertTrue(decision.matchedFeatureFlags().containsKey("flag1"));
    }
}
