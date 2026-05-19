package com.example.platform.federation.graphql;

import com.example.platform.federation.graphql.context.GraphQLRequestContext;
import com.example.platform.federation.graphql.dto.PromptTemplateDetail;
import com.example.platform.federation.graphql.resolver.PromptGraphQLResolver;
import com.example.platform.prompt.app.PromptTemplateService;
import com.example.platform.prompt.domain.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PromptTemplateDetailQueryTest {

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void returnsPromptTemplateDetail() throws Exception {
        PromptTemplateService promptService = mock(PromptTemplateService.class);

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", "ws-1", "user-1",
                List.of("MEMBER"), List.of("prompt:read"),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        PromptTemplate template = new PromptTemplate(
                "pt-1", "Test Template", "desc", "general",
                List.of("tag1"), "user-1", PromptTemplateStatus.ACTIVE,
                "1.0.0", "1.0.0", OffsetDateTime.now(), OffsetDateTime.now());
        when(promptService.getTemplate("pt-1")).thenReturn(template);

        PromptTemplateVersion version = new PromptTemplateVersion(
                "pv-1", "pt-1", "1.0.0", "body", "{}",
                "initial", "user-1", OffsetDateTime.now(), "abc123", null, false);
        when(promptService.listVersions("pt-1")).thenReturn(List.of(version));
        when(promptService.getCurrentVersion("pt-1")).thenReturn(version);

        PromptExecutionRun execution = new PromptExecutionRun(
                "pe-1", "pt-1", "1.0.0", "tenant-1", "user-1",
                "openai", "gpt-4", "hash", "preview", "{}",
                null, PromptExecutionStatus.SUCCEEDED, PromptRiskLevel.LOW,
                100, 0.003, OffsetDateTime.now(), OffsetDateTime.now(), null, null,
                null, null);
        when(promptService.listExecutions("pt-1")).thenReturn(List.of(execution));

        PromptGraphQLResolver resolver = new PromptGraphQLResolver();
        setField(resolver, "promptTemplateService", promptService);

        PromptTemplateDetail result = resolver.promptTemplateDetail("pt-1", ctx);

        assertNotNull(result);
        assertEquals("pt-1", result.id());
        assertEquals("Test Template", result.name());
        assertEquals("ACTIVE", result.status());
        assertEquals("1.0.0", result.currentVersion());
        assertFalse(result.versions().isEmpty());
        assertFalse(result.executions().isEmpty());
        assertEquals(1, result.versions().size());
        assertEquals("1.0.0", result.versions().getFirst().version());
        assertEquals(1, result.executions().size());
        assertEquals("pe-1", result.executions().getFirst().executionId());
    }

    @Test
    void limitsExecutionsTo20() throws Exception {
        PromptTemplateService promptService = mock(PromptTemplateService.class);

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of(), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        PromptTemplate template = new PromptTemplate(
                "pt-2", "T", "d", "g", List.of(), "u",
                PromptTemplateStatus.ACTIVE, "1.0.0", null,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(promptService.getTemplate("pt-2")).thenReturn(template);
        when(promptService.listVersions("pt-2")).thenReturn(List.of());
        when(promptService.getCurrentVersion("pt-2")).thenReturn(null);

        List<PromptExecutionRun> manyExecutions = java.util.stream.IntStream.range(0, 25)
                .mapToObj(i -> new PromptExecutionRun(
                        "pe-" + i, "pt-2", "1.0.0", "tenant-1", "user-1",
                        null, null, "hash", "preview", "{}",
                        null, PromptExecutionStatus.SUCCEEDED, PromptRiskLevel.LOW,
                        0, 0.0, OffsetDateTime.now(), OffsetDateTime.now(), null, null,
                        null, null))
                .toList();
        when(promptService.listExecutions("pt-2")).thenReturn(manyExecutions);

        PromptGraphQLResolver resolver = new PromptGraphQLResolver();
        setField(resolver, "promptTemplateService", promptService);

        PromptTemplateDetail result = resolver.promptTemplateDetail("pt-2", ctx);

        assertNotNull(result);
        assertEquals(20, result.executions().size());
    }

    @Test
    void handlesNoVersions() throws Exception {
        PromptTemplateService promptService = mock(PromptTemplateService.class);

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of(), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        PromptTemplate template = new PromptTemplate(
                "pt-3", "No Versions", "desc", "general",
                List.of(), "user-1", PromptTemplateStatus.ACTIVE,
                "1.0.0", null, OffsetDateTime.now(), OffsetDateTime.now());
        when(promptService.getTemplate("pt-3")).thenReturn(template);
        when(promptService.listVersions("pt-3")).thenReturn(List.of());
        when(promptService.getCurrentVersion("pt-3")).thenReturn(null);
        when(promptService.listExecutions("pt-3")).thenReturn(List.of());

        PromptGraphQLResolver resolver = new PromptGraphQLResolver();
        setField(resolver, "promptTemplateService", promptService);

        PromptTemplateDetail result = resolver.promptTemplateDetail("pt-3", ctx);

        assertNotNull(result);
        assertEquals("pt-3", result.id());
        assertNull(result.currentVersion());
        assertTrue(result.versions().isEmpty());
        assertTrue(result.executions().isEmpty());
    }

    @Test
    void includesTags() throws Exception {
        PromptTemplateService promptService = mock(PromptTemplateService.class);

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of(), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        PromptTemplate template = new PromptTemplate(
                "pt-4", "Tagged", "desc", "general",
                List.of("ai", "creative"), "user-1", PromptTemplateStatus.ACTIVE,
                "1.0.0", null, OffsetDateTime.now(), OffsetDateTime.now());
        when(promptService.getTemplate("pt-4")).thenReturn(template);
        when(promptService.listVersions("pt-4")).thenReturn(List.of());
        when(promptService.getCurrentVersion("pt-4")).thenReturn(null);
        when(promptService.listExecutions("pt-4")).thenReturn(List.of());

        PromptGraphQLResolver resolver = new PromptGraphQLResolver();
        setField(resolver, "promptTemplateService", promptService);

        PromptTemplateDetail result = resolver.promptTemplateDetail("pt-4", ctx);

        assertNotNull(result);
        assertNotNull(result.tags());
        assertEquals(2, result.tags().size());
        assertTrue(result.tags().contains("ai"));
        assertTrue(result.tags().contains("creative"));
    }

    @Test
    void mapsExecutionStatusCorrectly() throws Exception {
        PromptTemplateService promptService = mock(PromptTemplateService.class);

        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of(), List.of(),
                "GRAPHQL", "JWT_SESSION",
                "trace-1", "req-1",
                "127.0.0.1", "test-agent"
        );

        PromptTemplate template = new PromptTemplate(
                "pt-5", "Exec Test", "desc", "general",
                List.of(), "user-1", PromptTemplateStatus.ACTIVE,
                "1.0.0", null, OffsetDateTime.now(), OffsetDateTime.now());
        when(promptService.getTemplate("pt-5")).thenReturn(template);
        when(promptService.listVersions("pt-5")).thenReturn(List.of());
        when(promptService.getCurrentVersion("pt-5")).thenReturn(null);

        PromptExecutionRun failedExec = new PromptExecutionRun(
                "pe-fail", "pt-5", "1.0.0", "tenant-1", "user-1",
                "openai", "gpt-4", "hash", "preview", "{}",
                null, PromptExecutionStatus.FAILED, PromptRiskLevel.HIGH,
                0, 0.0, OffsetDateTime.now(), OffsetDateTime.now(), null, null,
                null, null);
        when(promptService.listExecutions("pt-5")).thenReturn(List.of(failedExec));

        PromptGraphQLResolver resolver = new PromptGraphQLResolver();
        setField(resolver, "promptTemplateService", promptService);

        PromptTemplateDetail result = resolver.promptTemplateDetail("pt-5", ctx);

        assertNotNull(result);
        assertEquals(1, result.executions().size());
        assertEquals("FAILED", result.executions().getFirst().status());
        assertEquals("HIGH", result.executions().getFirst().riskLevel());
    }
}
