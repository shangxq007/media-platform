package com.example.platform.federation.graphql.resolver;

import com.example.platform.billing.app.BillingProjectionService;
import com.example.platform.billing.app.UsageMeteringService;
import com.example.platform.extension.app.ExtensionRegistryService;
import com.example.platform.extension.app.ExtensionRegistryService.ExtensionInfo;
import com.example.platform.federation.graphql.context.GraphQLRequestContext;
import com.example.platform.federation.graphql.dto.AdminDashboard;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.dto.RenderJobResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminDashboardGraphQLResolverTest {

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void returnsDashboardForAdmin() throws Exception {
        RenderJobService renderJobService = mock(RenderJobService.class);
        BillingProjectionService billingProjectionService = mock(BillingProjectionService.class);
        UsageMeteringService usageMeteringService = mock(UsageMeteringService.class);
        ExtensionRegistryService extensionRegistryService = mock(ExtensionRegistryService.class);

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of("ADMIN"), List.of("admin:dashboard"),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        when(renderJobService.list()).thenReturn(List.of(
                new RenderJobResponse("rj-1", "proj-1", "snap-1", "1080p", "COMPLETED"),
                new RenderJobResponse("rj-2", "proj-1", "snap-2", "720p", "FAILED"),
                new RenderJobResponse("rj-3", "proj-2", "snap-3", "4k", "QUEUED")
        ));
        when(usageMeteringService.getUsage(null, null)).thenReturn(List.of());
        when(extensionRegistryService.listExtensions()).thenReturn(List.of(
                new ExtensionInfo("ext-1", "1.0.0", "PROVIDER", "PROVIDER", "ACTIVE", "SEMI_TRUSTED")
        ));

        AdminDashboardGraphQLResolver resolver = new AdminDashboardGraphQLResolver(renderJobService, billingProjectionService, usageMeteringService, extensionRegistryService);

        AdminDashboard result = resolver.adminDashboard("7d", ctx);

        assertNotNull(result);
        assertNotNull(result.renderStats());
        assertEquals(3, result.renderStats().submitted());
        assertEquals(1, result.renderStats().completed());
        assertEquals(1, result.renderStats().failed());
        assertNotNull(result.providerHealth());
        assertFalse(result.providerHealth().isEmpty());
        assertNotNull(result.billingSummary());
        assertNotNull(result.feedbackSummary());
        assertNotNull(result.extensionSummary());
        assertEquals(1, result.extensionSummary().installed());
        assertEquals(1, result.extensionSummary().enabled());
    }

    @Test
    void returnsDashboardForDashboardAdmin() throws Exception {
        RenderJobService renderJobService = mock(RenderJobService.class);
        BillingProjectionService billingProjectionService = mock(BillingProjectionService.class);
        UsageMeteringService usageMeteringService = mock(UsageMeteringService.class);
        ExtensionRegistryService extensionRegistryService = mock(ExtensionRegistryService.class);

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of("DASHBOARD_ADMIN"), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        when(renderJobService.list()).thenReturn(List.of());
        when(usageMeteringService.getUsage(null, null)).thenReturn(List.of());
        when(extensionRegistryService.listExtensions()).thenReturn(List.of());

        AdminDashboardGraphQLResolver resolver = new AdminDashboardGraphQLResolver(renderJobService, billingProjectionService, usageMeteringService, extensionRegistryService);

        AdminDashboard result = resolver.adminDashboard("7d", ctx);

        assertNotNull(result);
    }

    @Test
    void throwsForNonAdminUser() throws Exception {
        RenderJobService renderJobService = mock(RenderJobService.class);
        BillingProjectionService billingProjectionService = mock(BillingProjectionService.class);
        UsageMeteringService usageMeteringService = mock(UsageMeteringService.class);
        ExtensionRegistryService extensionRegistryService = mock(ExtensionRegistryService.class);

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of("MEMBER"), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        AdminDashboardGraphQLResolver resolver = new AdminDashboardGraphQLResolver(renderJobService, billingProjectionService, usageMeteringService, extensionRegistryService);

        assertThrows(IllegalArgumentException.class, () -> resolver.adminDashboard("7d", ctx));
    }

    @Test
    void usesDefaultRange() throws Exception {
        RenderJobService renderJobService = mock(RenderJobService.class);
        BillingProjectionService billingProjectionService = mock(BillingProjectionService.class);
        UsageMeteringService usageMeteringService = mock(UsageMeteringService.class);
        ExtensionRegistryService extensionRegistryService = mock(ExtensionRegistryService.class);

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of("ADMIN"), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        when(renderJobService.list()).thenReturn(List.of());
        when(usageMeteringService.getUsage(null, null)).thenReturn(List.of());
        when(extensionRegistryService.listExtensions()).thenReturn(List.of());

        AdminDashboardGraphQLResolver resolver = new AdminDashboardGraphQLResolver(renderJobService, billingProjectionService, usageMeteringService, extensionRegistryService);

        AdminDashboard result = resolver.adminDashboard(null, ctx);
        assertNotNull(result);
    }
}
