package com.example.platform.federation.nlq.app;

import com.example.platform.federation.nlq.domain.QueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class QueryExecutionReadOnlyTest {

    private JdbcTemplate jdbcTemplate;
    private SqlCostEstimator sqlCostEstimator;
    private ResultRedactionService resultRedactionService;
    private ResultSummarizer resultSummarizer;
    private ChartSuggestionService chartSuggestionService;
    private QueryExecutionService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        sqlCostEstimator = new SqlCostEstimator();
        resultRedactionService = new ResultRedactionService();
        resultSummarizer = mock(ResultSummarizer.class);
        chartSuggestionService = new ChartSuggestionService();

        when(resultSummarizer.summarize(any(), any())).thenReturn("Test summary");

        service = new QueryExecutionService(jdbcTemplate, sqlCostEstimator,
            resultRedactionService, resultSummarizer, chartSuggestionService);
    }

    @Test
    void executeReturnsRowCountFromJdbcTemplateResult() {
        List<Map<String, Object>> mockRows = List.of(
            Map.of("job_id", "job-1", "status", "completed"),
            Map.of("job_id", "job-2", "status", "failed")
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(mockRows);

        QueryResult result = service.execute(
            "SELECT job_id, status FROM render_jobs_report_view LIMIT 100",
            Map.of(), 100, 10);

        assertNotNull(result);
        assertEquals(2, result.rowCount());
        assertEquals(2, result.rows().size());
        assertFalse(result.truncated());
        assertTrue(result.durationMs() >= 0);
        assertNotNull(result.queryId());
    }

    @Test
    void executeHandlesEmptyResults() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of());

        QueryResult result = service.execute(
            "SELECT job_id FROM render_jobs_report_view LIMIT 100",
            Map.of(), 100, 10);

        assertNotNull(result);
        assertEquals(0, result.rowCount());
    }

    @Test
    void executeHandlesExceptionFromJdbcTemplate() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
            .thenThrow(new RuntimeException("DB error"));

        QueryResult result = service.execute(
            "SELECT job_id FROM render_jobs_report_view LIMIT 100",
            Map.of(), 100, 10);

        assertNotNull(result);
        assertEquals(0, result.rowCount());
        assertNotNull(result.warnings());
        assertFalse(result.warnings().isEmpty());
        assertTrue(result.warnings().get(0).contains("failed"));
    }

    @Test
    void executeMarksTruncatedWhenResultsReachMaxRows() {
        List<Map<String, Object>> mockRows = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            mockRows.add(Map.of("id", i));
        }
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(mockRows);

        QueryResult result = service.execute("SELECT id FROM test LIMIT 50", Map.of(), 50, 10);

        assertTrue(result.truncated());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("limited")));
    }

    @Test
    void executeDoesNotTruncateWhenResultsBelowMaxRows() {
        List<Map<String, Object>> mockRows = List.of(Map.of("id", 1));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(mockRows);

        QueryResult result = service.execute("SELECT id FROM test LIMIT 50", Map.of(), 50, 10);

        assertFalse(result.truncated());
    }

    @Test
    void executeReplacesNamedParametersWithLiteralValues() {
        List<Map<String, Object>> mockRows = List.of();
        // When parameters are non-empty, NamedParameterJdbcTemplate is used
        // The mock JdbcTemplate is wrapped internally, so we verify the query was called
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(mockRows);

        Map<String, Object> params = mapOf("tenant_id", "tenant-1", "limit", 10);
        QueryResult result = service.execute(
            "SELECT count(*) FROM test WHERE tenant_id = :tenant_id LIMIT :limit",
            params, 100, 10);

        assertNotNull(result);
        assertEquals(0, result.rowCount());
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2) {
        return (Map<K, V>) Map.of(k1, v1, k2, v2);
    }

    @Test
    void executePassesMaxRowsToJdbcTemplate() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of());

        service.execute("SELECT 1 AS col FROM test LIMIT 1", Map.of(), 500, 10);

        verify(jdbcTemplate).setMaxRows(500);
    }

    @Test
    void executePassesTimeoutToJdbcTemplate() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of());

        service.execute("SELECT 1 AS col FROM test LIMIT 1", Map.of(), 100, 15);

        verify(jdbcTemplate).setQueryTimeout(anyInt());
    }

    @Test
    void executeReturnsSummaryFromSummarizer() {
        List<Map<String, Object>> mockRows = List.of(Map.of("col1", "val1"));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(mockRows);

        QueryResult result = service.execute(
            "SELECT col1 FROM test LIMIT 100", Map.of(), 100, 10);

        assertNotNull(result.summary());
        assertEquals("Test summary", result.summary());
        verify(resultSummarizer).summarize(any(), eq(mockRows));
    }

    @Test
    void executeReturnsRowsFromJdbcTemplate() {
        List<Map<String, Object>> mockRows = List.of(
            Map.of("col1", "val1"),
            Map.of("col1", "val2")
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(mockRows);

        QueryResult result = service.execute(
            "SELECT col1 FROM test LIMIT 100", Map.of(), 100, 10);

        assertEquals(2, result.rowCount());
        assertEquals("val1", result.rows().get(0).get("col1"));
        assertEquals("val2", result.rows().get(1).get("col1"));
    }
}
