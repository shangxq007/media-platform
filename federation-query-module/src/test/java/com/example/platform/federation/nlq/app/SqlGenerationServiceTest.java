package com.example.platform.federation.nlq.app;

import com.example.platform.ai.api.AiGatewayPort;
import com.example.platform.ai.domain.ChatResult;
import com.example.platform.federation.nlq.domain.SqlDraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SqlGenerationServiceTest {

    private AiGatewayPort aiGatewayPort;
    private SqlGenerationService service;

    @BeforeEach
    void setUp() {
        aiGatewayPort = mock(AiGatewayPort.class);
        when(aiGatewayPort.chat(anyString(), anyString()))
            .thenReturn(new ChatResult("stub", "local-dev-model", "stub response"));
        service = new SqlGenerationService(aiGatewayPort);
    }

    @Test
    void classifyIntentAggregation() {
        assertEquals("AGGREGATION", service.classifyIntent("How many render jobs completed?"));
        assertEquals("AGGREGATION", service.classifyIntent("What is the total cost?"));
        assertEquals("AGGREGATION", service.classifyIntent("Count the number of failed jobs"));
        assertEquals("AGGREGATION", service.classifyIntent("Average duration of renders"));
        assertEquals("AGGREGATION", service.classifyIntent("How much did we spend?"));
    }

    @Test
    void classifyIntentTrend() {
        assertEquals("TREND", service.classifyIntent("Show render jobs over time"));
        assertEquals("TREND", service.classifyIntent("Daily trend of failed jobs"));
        assertEquals("TREND", service.classifyIntent("Weekly usage pattern"));
        assertEquals("TREND", service.classifyIntent("Monthly cost trend"));
    }

    @Test
    void classifyIntentComparison() {
        assertEquals("COMPARISON", service.classifyIntent("Compare completed vs failed jobs"));
        assertEquals("COMPARISON", service.classifyIntent("Difference between render providers"));
        assertEquals("COMPARISON", service.classifyIntent("Cost versus last month"));
    }

    @Test
    void classifyIntentRanking() {
        assertEquals("RANKING", service.classifyIntent("Top 10 longest render jobs"));
        assertEquals("RANKING", service.classifyIntent("Bottom 5 by cost"));
        assertEquals("RANKING", service.classifyIntent("Highest cost renders"));
    }

    @Test
    void classifyIntentDistribution() {
        assertEquals("DISTRIBUTION", service.classifyIntent("Distribution of jobs by status"));
        assertEquals("DISTRIBUTION", service.classifyIntent("Breakdown by category"));
    }

    @Test
    void classifyIntentDetail() {
        assertEquals("DETAIL", service.classifyIntent("Show me render jobs"));
        assertEquals("DETAIL", service.classifyIntent("List all artifacts"));
    }

    @Test
    void generateSqlReturnsSqlDraft() {
        SqlDraft draft = service.generateSql("How many render jobs?", Map.of(), List.of("render_jobs_report_view"));
        assertNotNull(draft);
        assertNotNull(draft.draftId());
        assertTrue(draft.draftId().startsWith("sql_"));
        assertEquals("How many render jobs?", draft.question());
        assertNotNull(draft.sql());
        assertFalse(draft.sql().isEmpty());
    }

    @Test
    void generateSqlUsesIntentClassification() {
        SqlDraft draft = service.generateSql("Show render jobs over time", Map.of(), List.of("render_jobs_report_view"));
        assertEquals("TREND", draft.intent());
    }

    @Test
    void generateSqlContainsDataset() {
        SqlDraft draft = service.generateSql("How many render jobs?", Map.of(), List.of("render_jobs_report_view"));
        assertTrue(draft.datasetKeys().contains("render_jobs_report_view"));
    }

    @Test
    void generateSqlHasParameters() {
        SqlDraft draft = service.generateSql("Show render jobs", Map.of(), List.of("render_jobs_report_view"));
        assertNotNull(draft.parameters());
        assertFalse(draft.parameters().isEmpty());
    }

    @Test
    void generateSqlHasAssumptions() {
        SqlDraft draft = service.generateSql("Show render jobs", Map.of(), List.of("render_jobs_report_view"));
        assertNotNull(draft.assumptions());
        assertFalse(draft.assumptions().isEmpty());
    }

    @Test
    void generateSqlHasChartSuggestions() {
        SqlDraft draft = service.generateSql("Show render jobs over time", Map.of(), List.of("render_jobs_report_view"));
        assertNotNull(draft.chartSuggestions());
        assertFalse(draft.chartSuggestions().isEmpty());
    }

    @Test
    void generateSqlSetsConfidence() {
        SqlDraft draft = service.generateSql("Show render jobs", Map.of(), List.of("render_jobs_report_view"));
        assertTrue(draft.confidence() > 0);
        assertTrue(draft.confidence() <= 1.0);
    }

    @Test
    void generateSqlSetsCreatedAt() {
        SqlDraft draft = service.generateSql("Show render jobs", Map.of(), List.of("render_jobs_report_view"));
        assertNotNull(draft.createdAt());
    }

    @Test
    void generateSqlWithCustomContext() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("limit", 50);
        context.put("time_range_days", 7);
        context.put("tenant_id", "tenant_abc");

        SqlDraft draft = service.generateSql("Total cost", context, List.of("billing_usage_report_view"));
        assertEquals(50, draft.parameters().get("limit"));
        assertEquals(7, draft.parameters().get("time_range_days"));
    }

    @Test
    void generateSqlResolvesBillingDataset() {
        SqlDraft draft = service.generateSql("What is the total billing cost?", Map.of(), List.of("billing_usage_report_view", "render_jobs_report_view"));
        assertEquals("billing_usage_report_view", draft.datasetKeys().get(0));
    }

    @Test
    void generateSqlResolvesPromptDataset() {
        SqlDraft draft = service.generateSql("Show AI prompt executions", Map.of(), List.of("render_jobs_report_view", "prompt_execution_report_view"));
        assertEquals("prompt_execution_report_view", draft.datasetKeys().get(0));
    }

    @Test
    void generateSqlResolvesAuditDataset() {
        SqlDraft draft = service.generateSql("Show audit events from the audit log", Map.of(), List.of("render_jobs_report_view", "audit_event_report_view"));
        assertEquals("audit_event_report_view", draft.datasetKeys().get(0));
    }

    @Test
    void generateSqlContainsLimitClause() {
        SqlDraft draft = service.generateSql("Show render jobs", Map.of(), List.of("render_jobs_report_view"));
        assertTrue(draft.sql().toUpperCase().contains("LIMIT"));
    }

    @Test
    void generateSqlContainsTimeRange() {
        SqlDraft draft = service.generateSql("Show render jobs", Map.of(), List.of("render_jobs_report_view"));
        assertTrue(draft.sql().contains("CURRENT_DATE") || draft.sql().contains("INTERVAL"));
    }

    @Test
    void generateSqlRiskLevelLow() {
        SqlDraft draft = service.generateSql("Show render jobs", Map.of(), List.of("render_jobs_report_view"));
        assertEquals("LOW", draft.riskLevel());
    }

    @Test
    void generateSqlRequiredPermissions() {
        SqlDraft draft = service.generateSql("Show render jobs", Map.of(), List.of("render_jobs_report_view"));
        assertTrue(draft.requiredPermissions().contains("analytics.read"));
    }

    @Test
    void generateSqlFallbackOnAiError() {
        when(aiGatewayPort.chat(anyString(), anyString()))
            .thenThrow(new RuntimeException("AI service unavailable"));

        SqlDraft draft = service.generateSql("Show render jobs", Map.of(), List.of("render_jobs_report_view"));
        assertNotNull(draft);
        assertFalse(draft.sql().isEmpty());
    }

    @Test
    void generateSqlFallbackOnStubProvider() {
        when(aiGatewayPort.chat(anyString(), anyString()))
            .thenReturn(new ChatResult("stub", "local-dev-model", "SELECT 1"));

        SqlDraft draft = service.generateSql("Show render jobs", Map.of(), List.of("render_jobs_report_view"));
        assertNotNull(draft);
        assertFalse(draft.sql().isEmpty());
    }

    @Test
    void generateSqlForAggregationIntent() {
        SqlDraft draft = service.generateSql("How many render jobs completed?", Map.of(), List.of("render_jobs_report_view"));
        assertEquals("AGGREGATION", draft.intent());
        assertTrue(draft.sql().toUpperCase().contains("COUNT"));
    }

    @Test
    void generateSqlForTrendIntent() {
        SqlDraft draft = service.generateSql("Show render jobs over time", Map.of(), List.of("render_jobs_report_view"));
        assertEquals("TREND", draft.intent());
        assertTrue(draft.sql().toUpperCase().contains("DATE_TRUNC"));
    }

    @Test
    void generateSqlForRankingIntent() {
        SqlDraft draft = service.generateSql("Top 10 longest render jobs", Map.of(), List.of("render_jobs_report_view"));
        assertEquals("RANKING", draft.intent());
        assertTrue(draft.sql().toUpperCase().contains("ORDER BY"));
    }

    @Test
    void generateSqlForDistributionIntent() {
        SqlDraft draft = service.generateSql("Distribution of jobs by status", Map.of(), List.of("render_jobs_report_view"));
        assertEquals("DISTRIBUTION", draft.intent());
        assertTrue(draft.sql().toUpperCase().contains("GROUP BY"));
    }

    @Test
    void generateSqlForComparisonIntent() {
        SqlDraft draft = service.generateSql("Compare completed vs failed", Map.of(), List.of("render_jobs_report_view"));
        assertEquals("COMPARISON", draft.intent());
        assertTrue(draft.sql().toUpperCase().contains("GROUP BY"));
    }
}
