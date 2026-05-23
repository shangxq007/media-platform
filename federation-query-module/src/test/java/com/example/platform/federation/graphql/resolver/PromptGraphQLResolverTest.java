package com.example.platform.federation.graphql.resolver;

import com.example.platform.federation.graphql.context.GraphQLRequestContext;
import com.example.platform.federation.graphql.dto.PromptTemplateDetail;
import com.example.platform.prompt.app.PromptTemplateService;
import com.example.platform.prompt.domain.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PromptGraphQLResolverTest {

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

        PromptGraphQLResolver resolver = new PromptGraphQLResolver(promptService);

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

        PromptGraphQLResolver resolver = new PromptGraphQLResolver(promptService);

        PromptTemplateDetail result = resolver.promptTemplateDetail("pt-2", ctx);

        assertNotNull(result);
        assertEquals(20, result.executions().size());
    }
}
