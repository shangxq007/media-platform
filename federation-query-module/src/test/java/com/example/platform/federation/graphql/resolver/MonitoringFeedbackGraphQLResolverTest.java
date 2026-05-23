package com.example.platform.federation.graphql.resolver;

import com.example.platform.federation.graphql.context.GraphQLRequestContext;
import com.example.platform.federation.graphql.dto.MonitoringFeedbackOverview;
import com.example.platform.prompt.app.PromptTemplateService;
import com.example.platform.prompt.domain.PromptExecutionRun;
import com.example.platform.prompt.domain.PromptExecutionStatus;
import com.example.platform.prompt.domain.PromptRiskLevel;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.dto.RenderJobResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonitoringFeedbackGraphQLResolverTest {

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void returnsMonitoringFeedbackOverview() throws Exception {
        RenderJobService renderJobService = mock(RenderJobService.class);
        PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", "ws-1", "user-1",
                List.of("MEMBER"), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        when(renderJobService.list()).thenReturn(List.of(
                new RenderJobResponse("rj-1", "proj-1", "snap-1", "1080p", "COMPLETED"),
                new RenderJobResponse("rj-2", "proj-1", "snap-2", "720p", "FAILED")
        ));

        when(promptTemplateService.listAllExecutions()).thenReturn(List.of(
                new PromptExecutionRun(
                        "pe-1", "pt-1", "1.0.0", "tenant-1", "user-1",
                        "openai", "gpt-4", "hash", "preview", "{}",
                        null, PromptExecutionStatus.FAILED, PromptRiskLevel.HIGH,
                        100, 0.003, OffsetDateTime.now(), OffsetDateTime.now(),
                        "ERR-001", null, null, null)
        ));

        MonitoringFeedbackGraphQLResolver resolver = new MonitoringFeedbackGraphQLResolver(renderJobService, promptTemplateService);

        MonitoringFeedbackOverview result = resolver.monitoringFeedbackOverview("7d", ctx);

        assertNotNull(result);
        assertNotNull(result.monitoringStatus());
        assertTrue(result.monitoringStatus().sentryEnabled());
        assertTrue(result.monitoringStatus().openReplayEnabled());
        assertNotNull(result.feedbackSummary());
        assertEquals(1, result.feedbackSummary().linkedRenderJobs());
        assertEquals(1, result.feedbackSummary().linkedPromptExecutions());
        assertNotNull(result.problematicDataSummary());
    }

    @Test
    void usesDefaultRange() throws Exception {
        RenderJobService renderJobService = mock(RenderJobService.class);
        PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of(), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        when(renderJobService.list()).thenReturn(List.of());
        when(promptTemplateService.listAllExecutions()).thenReturn(List.of());

        MonitoringFeedbackGraphQLResolver resolver = new MonitoringFeedbackGraphQLResolver(renderJobService, promptTemplateService);

        MonitoringFeedbackOverview result = resolver.monitoringFeedbackOverview(null, ctx);

        assertNotNull(result);
        assertEquals(0, result.feedbackSummary().linkedRenderJobs());
        assertEquals(0, result.feedbackSummary().linkedPromptExecutions());
    }

    @Test
    void handlesEmptyFailedJobs() throws Exception {
        RenderJobService renderJobService = mock(RenderJobService.class);
        PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of(), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        when(renderJobService.list()).thenReturn(List.of(
                new RenderJobResponse("rj-1", "proj-1", "snap-1", "1080p", "COMPLETED")
        ));
        when(promptTemplateService.listAllExecutions()).thenReturn(List.of(
                new PromptExecutionRun(
                        "pe-1", "pt-1", "1.0.0", "tenant-1", "user-1",
                        null, null, "hash", "preview", "{}",
                        null, PromptExecutionStatus.SUCCEEDED, PromptRiskLevel.LOW,
                        0, 0.0, OffsetDateTime.now(), OffsetDateTime.now(), null, null,
                        null, null)
        ));

        MonitoringFeedbackGraphQLResolver resolver = new MonitoringFeedbackGraphQLResolver(renderJobService, promptTemplateService);

        MonitoringFeedbackOverview result = resolver.monitoringFeedbackOverview("7d", ctx);

        assertNotNull(result);
        assertEquals(0, result.feedbackSummary().linkedRenderJobs());
        assertEquals(0, result.feedbackSummary().linkedPromptExecutions());
    }
}
