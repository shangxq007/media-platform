package com.example.platform.federation.graphql.resolver;

import com.example.platform.extension.api.port.ExtensionQueries;
import com.example.platform.extension.api.port.ExtensionLimitQueries;
import com.example.platform.extension.api.port.ExtensionRoutingQueries;
import com.example.platform.extension.domain.ExtensionResourceLimits;
import com.example.platform.extension.domain.RoutingRule;
import com.example.platform.federation.graphql.context.GraphQLRequestContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExtensionGraphQLResolverTest {
    @org.junit.jupiter.api.AfterEach void clearOwnerFixtureScope(){com.example.platform.shared.web.TenantContext.clear();}


    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void returnsExtensionsForExtensionAdmin() throws Exception {
        ExtensionQueries registryService = mock(ExtensionQueries.class);
        ExtensionRoutingQueries router = mock(ExtensionRoutingQueries.class);
        ExtensionLimitQueries limiter = mock(ExtensionLimitQueries.class);

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of("EXTENSION_ADMIN"), List.of("extension:read"),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        when(registryService.listExtensions()).thenReturn(List.of(
                new com.example.platform.extension.api.port.ExtensionQueries.ExtensionInfo("ext-1", "1.0.0", "PROVIDER", "PROVIDER", "ACTIVE", "SEMI_TRUSTED"),
                new com.example.platform.extension.api.port.ExtensionQueries.ExtensionInfo("ext-2", "2.0.0", "PROMPT", "PROMPT", "ACTIVE", "UNTRUSTED")
        ));

        RoutingRule rule = new RoutingRule(
                "r-1", "rule-1", "ext-1", null, "1.0.0",
                null, null, "render", 100, 50, true);
        when(router.getRules("ext-1")).thenReturn(List.of(rule));
        when(router.getRules("ext-2")).thenReturn(List.of());

        ExtensionResourceLimits limits = new ExtensionResourceLimits(
                4, 256, 50, 100, 10 * 1024 * 1024, 4 * 1024 * 1024, 30_000);
        when(limiter.getLimits("ext-1")).thenReturn(limits);
        when(limiter.getLimits("ext-2")).thenReturn(ExtensionResourceLimits.UNTRUSTED);

        when(router.routes(anyString(), any())).thenAnswer(call -> router.getRules(call.getArgument(0)).stream().map(r -> new com.example.platform.extension.api.port.ExtensionRoutingQueries.Route(r.scene(),r.priority(),r.enabled())).toList());
        when(limiter.limits(anyString())).thenAnswer(call -> {var v=limiter.getLimits(call.getArgument(0)); return new com.example.platform.extension.api.port.ExtensionLimitQueries.Limits(v.timeoutMs(),v.maxConcurrency(),v.maxOutputBytes());});
        ExtensionGraphQLResolver resolver = new ExtensionGraphQLResolver(registryService, router, limiter, com.example.platform.federation.graphql.OwnerQueryFixtures.scope());

        List<com.example.platform.federation.graphql.dto.ExtensionInfo> result = resolver.extensionOverview(ctx);

        assertNotNull(result);
        assertEquals(2, result.size());

        com.example.platform.federation.graphql.dto.ExtensionInfo first = result.get(0);
        assertEquals("ext-1", first.extensionKey());
        assertEquals("PROVIDER", first.runtimeType());
        assertEquals("SEMI_TRUSTED", first.trustLevel());
        assertTrue(first.enabled());
        assertEquals("1.0.0", first.version());
        assertEquals("UNKNOWN", first.healthStatus());
        assertFalse(first.routeRules().isEmpty());
        assertNotNull(first.resourceLimits());
        assertEquals(30000, first.resourceLimits().timeoutMs());
        assertEquals(4, first.resourceLimits().maxConcurrency());

        com.example.platform.federation.graphql.dto.ExtensionInfo second = result.get(1);
        assertEquals("ext-2", second.extensionKey());
        assertTrue(second.routeRules().isEmpty());
    }

    @Test
    void returnsExtensionsForAdmin() throws Exception {
        ExtensionQueries registryService = mock(ExtensionQueries.class);
        ExtensionRoutingQueries router = mock(ExtensionRoutingQueries.class);
        ExtensionLimitQueries limiter = mock(ExtensionLimitQueries.class);

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of("ADMIN"), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        when(registryService.listExtensions()).thenReturn(List.of());
        when(router.getRules(any())).thenReturn(List.of());
        when(limiter.getLimits(any())).thenReturn(ExtensionResourceLimits.DEFAULTS);

        when(router.routes(anyString(), any())).thenAnswer(call -> router.getRules(call.getArgument(0)).stream().map(r -> new com.example.platform.extension.api.port.ExtensionRoutingQueries.Route(r.scene(),r.priority(),r.enabled())).toList());
        when(limiter.limits(anyString())).thenAnswer(call -> {var v=limiter.getLimits(call.getArgument(0)); return new com.example.platform.extension.api.port.ExtensionLimitQueries.Limits(v.timeoutMs(),v.maxConcurrency(),v.maxOutputBytes());});
        ExtensionGraphQLResolver resolver = new ExtensionGraphQLResolver(registryService, router, limiter, com.example.platform.federation.graphql.OwnerQueryFixtures.scope());

        List<com.example.platform.federation.graphql.dto.ExtensionInfo> result = resolver.extensionOverview(ctx);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void throwsForUnauthorizedUser() throws Exception {
        ExtensionQueries registryService = mock(ExtensionQueries.class);
        ExtensionRoutingQueries router = mock(ExtensionRoutingQueries.class);
        ExtensionLimitQueries limiter = mock(ExtensionLimitQueries.class);

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of("MEMBER"), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        when(router.routes(anyString(), any())).thenAnswer(call -> router.getRules(call.getArgument(0)).stream().map(r -> new com.example.platform.extension.api.port.ExtensionRoutingQueries.Route(r.scene(),r.priority(),r.enabled())).toList());
        when(limiter.limits(anyString())).thenAnswer(call -> {var v=limiter.getLimits(call.getArgument(0)); return new com.example.platform.extension.api.port.ExtensionLimitQueries.Limits(v.timeoutMs(),v.maxConcurrency(),v.maxOutputBytes());});
        ExtensionGraphQLResolver resolver = new ExtensionGraphQLResolver(registryService, router, limiter, com.example.platform.federation.graphql.OwnerQueryFixtures.scope());

        assertThrows(IllegalArgumentException.class, () -> resolver.extensionOverview(ctx));
    }

    @Test
    void throwsForNullRoles() throws Exception {
        ExtensionQueries registryService = mock(ExtensionQueries.class);
        ExtensionRoutingQueries router = mock(ExtensionRoutingQueries.class);
        ExtensionLimitQueries limiter = mock(ExtensionLimitQueries.class);

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                null, null,
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        when(router.routes(anyString(), any())).thenAnswer(call -> router.getRules(call.getArgument(0)).stream().map(r -> new com.example.platform.extension.api.port.ExtensionRoutingQueries.Route(r.scene(),r.priority(),r.enabled())).toList());
        when(limiter.limits(anyString())).thenAnswer(call -> {var v=limiter.getLimits(call.getArgument(0)); return new com.example.platform.extension.api.port.ExtensionLimitQueries.Limits(v.timeoutMs(),v.maxConcurrency(),v.maxOutputBytes());});
        ExtensionGraphQLResolver resolver = new ExtensionGraphQLResolver(registryService, router, limiter, com.example.platform.federation.graphql.OwnerQueryFixtures.scope());

        assertThrows(IllegalArgumentException.class, () -> resolver.extensionOverview(ctx));
    }
}
