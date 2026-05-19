package com.example.platform.federation.graphql;

import com.example.platform.billing.app.BillingProjectionService;
import com.example.platform.billing.app.UsageMeteringService;
import com.example.platform.extension.app.ExtensionRegistryService;
import com.example.platform.federation.graphql.context.GraphQLRequestContext;
import com.example.platform.federation.graphql.dto.AdminDashboard;
import com.example.platform.federation.graphql.resolver.AdminDashboardGraphQLResolver;
import com.example.platform.render.app.RenderJobService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminDashboardAccessDeniedTest {

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private AdminDashboardGraphQLResolver createResolver() throws Exception {
        RenderJobService renderJobService = mock(RenderJobService.class);
        BillingProjectionService billingProjectionService = mock(BillingProjectionService.class);
        UsageMeteringService usageMeteringService = mock(UsageMeteringService.class);
        ExtensionRegistryService extensionRegistryService = mock(ExtensionRegistryService.class);

        AdminDashboardGraphQLResolver resolver = new AdminDashboardGraphQLResolver();
        setField(resolver, "renderJobService", renderJobService);
        setField(resolver, "billingProjectionService", billingProjectionService);
        setField(resolver, "usageMeteringService", usageMeteringService);
        setField(resolver, "extensionRegistryService", extensionRegistryService);
        return resolver;
    }

    @Test
    void throwsForNonAdminUser() throws Exception {
        AdminDashboardGraphQLResolver resolver = createResolver();

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of("MEMBER"), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        assertThrows(IllegalArgumentException.class, () -> resolver.adminDashboard("7d", ctx));
    }

    @Test
    void throwsForBasicUserRole() throws Exception {
        AdminDashboardGraphQLResolver resolver = createResolver();

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of("USER"), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> resolver.adminDashboard("7d", ctx));
        assertTrue(ex.getMessage().contains("Access denied"));
    }

    @Test
    void throwsForEmptyRoles() throws Exception {
        AdminDashboardGraphQLResolver resolver = createResolver();

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of(), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        assertThrows(IllegalArgumentException.class, () -> resolver.adminDashboard("7d", ctx));
    }

    @Test
    void allowsAdminRole() throws Exception {
        AdminDashboardGraphQLResolver resolver = createResolver();

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of("ADMIN"), List.of("admin:dashboard"),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        AdminDashboard result = resolver.adminDashboard("7d", ctx);
        assertNotNull(result);
    }

    @Test
    void allowsDashboardAdminRole() throws Exception {
        AdminDashboardGraphQLResolver resolver = createResolver();

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of("DASHBOARD_ADMIN"), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        AdminDashboard result = resolver.adminDashboard("7d", ctx);
        assertNotNull(result);
    }

    @Test
    void allowsAdminAmongMultipleRoles() throws Exception {
        AdminDashboardGraphQLResolver resolver = createResolver();

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of("MEMBER", "ADMIN"), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        AdminDashboard result = resolver.adminDashboard("7d", ctx);
        assertNotNull(result);
    }

    @Test
    void throwsForViewerRole() throws Exception {
        AdminDashboardGraphQLResolver resolver = createResolver();

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of("VIEWER"), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        assertThrows(IllegalArgumentException.class, () -> resolver.adminDashboard("7d", ctx));
    }

    @Test
    void errorMessageMentionsRequiredRoles() throws Exception {
        AdminDashboardGraphQLResolver resolver = createResolver();

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of("MEMBER"), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> resolver.adminDashboard("7d", ctx));
        assertTrue(ex.getMessage().contains("ADMIN") || ex.getMessage().contains("DASHBOARD_ADMIN"));
    }

    @Test
    void usesDefaultRangeWhenNull() throws Exception {
        AdminDashboardGraphQLResolver resolver = createResolver();

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of("ADMIN"), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        AdminDashboard result = resolver.adminDashboard(null, ctx);
        assertNotNull(result);
    }

    @Test
    void usesDefaultRangeWhenBlank() throws Exception {
        AdminDashboardGraphQLResolver resolver = createResolver();

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of("ADMIN"), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        AdminDashboard result = resolver.adminDashboard("  ", ctx);
        assertNotNull(result);
    }
}
