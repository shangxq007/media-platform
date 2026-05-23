package com.example.platform.federation.graphql.resolver;

import com.example.platform.entitlement.app.EntitlementDecisionService;
import com.example.platform.entitlement.domain.AccessCheckRequest;
import com.example.platform.entitlement.domain.EntitlementDecision;
import com.example.platform.federation.graphql.context.GraphQLRequestContext;
import com.example.platform.federation.graphql.dto.ExportPanelState;
import com.example.platform.identity.app.ProjectRepository;
import com.example.platform.identity.domain.Project;
import com.example.platform.identity.domain.Project.ProjectStatus;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.infrastructure.ExportPolicyService;
import com.example.platform.render.infrastructure.ExportPolicyService.ExportPreset;
import com.example.platform.render.infrastructure.ExportPolicyService.ExportTier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExportPanelGraphQLResolverTest {

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void returnsExportPanelStateForValidProject() throws Exception {
        RenderJobService renderJobService = mock(RenderJobService.class);
        ExportPolicyService exportPolicyService = mock(ExportPolicyService.class);
        EntitlementDecisionService entitlementService = mock(EntitlementDecisionService.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of("MEMBER"), List.of("export"),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        Project project = new Project("proj-1", "tenant-1", "Test Project", "desc", ProjectStatus.ACTIVE, Instant.now());
        when(projectRepository.findById("proj-1")).thenReturn(Optional.of(project));

        when(renderJobService.listByProject("tenant-1", "proj-1")).thenReturn(List.of());

        EntitlementDecision decision = new EntitlementDecision(
                true, "ALLOW", "TIER", "Access granted", "PRO",
                List.of("tier:PRO"), null, null, null, null,
                null, List.of(), null, false);
        when(entitlementService.evaluate(any(AccessCheckRequest.class))).thenReturn(decision);

        ExportPreset preset = new ExportPreset("pro_1080p", "Pro 1080p",
                "1920x1080", 30, "mp4", "h264", "aac", false, "PRO", "javacv");
        when(exportPolicyService.getAvailablePresets("PRO")).thenReturn(List.of(preset));
        when(exportPolicyService.isPresetAvailable("pro_1080p", "PRO")).thenReturn(true);
        when(exportPolicyService.getDefaultPreset("PRO")).thenReturn(preset);
        when(exportPolicyService.resolveProvider("pro_1080p", "PRO")).thenReturn("javacv");

        ExportPanelGraphQLResolver resolver = new ExportPanelGraphQLResolver(renderJobService, exportPolicyService, entitlementService, projectRepository);

        ExportPanelState result = resolver.exportPanelState("proj-1", ctx);

        assertNotNull(result);
        assertNotNull(result.project());
        assertEquals("proj-1", result.project().id());
        assertEquals("Test Project", result.project().name());
        assertNotNull(result.timelineSummary());
        assertNotNull(result.exportOptions());
        assertNotNull(result.workers());
        assertNotNull(result.validation());
    }

    @Test
    void throwsForProjectNotInTenant() throws Exception {
        RenderJobService renderJobService = mock(RenderJobService.class);
        ExportPolicyService exportPolicyService = mock(ExportPolicyService.class);
        EntitlementDecisionService entitlementService = mock(EntitlementDecisionService.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-2", null, "user-1",
                List.of("MEMBER"), List.of("export"),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        Project project = new Project("proj-1", "tenant-1", "Test Project", "desc", ProjectStatus.ACTIVE, Instant.now());
        when(projectRepository.findById("proj-1")).thenReturn(Optional.of(project));

        ExportPanelGraphQLResolver resolver = new ExportPanelGraphQLResolver(renderJobService, exportPolicyService, entitlementService, projectRepository);

        assertThrows(IllegalArgumentException.class, () -> resolver.exportPanelState("proj-1", ctx));
    }

    @Test
    void throwsForMissingProject() throws Exception {
        RenderJobService renderJobService = mock(RenderJobService.class);
        ExportPolicyService exportPolicyService = mock(ExportPolicyService.class);
        EntitlementDecisionService entitlementService = mock(EntitlementDecisionService.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of("MEMBER"), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        when(projectRepository.findById("nonexistent")).thenReturn(Optional.empty());

        ExportPanelGraphQLResolver resolver = new ExportPanelGraphQLResolver(renderJobService, exportPolicyService, entitlementService, projectRepository);

        assertThrows(IllegalArgumentException.class, () -> resolver.exportPanelState("nonexistent", ctx));
    }
}
