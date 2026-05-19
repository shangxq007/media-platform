package com.example.platform.federation.nlq.app;

import com.example.platform.federation.nlq.domain.SqlSafetyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SqlSafetyValidatorTest {

    private SqlSafetyValidator validator;
    private Set<String> allowedDatasets;

    @BeforeEach
    void setUp() {
        validator = new SqlSafetyValidator();
        allowedDatasets = Set.of("render_jobs_report_view", "artifact_report_view",
            "prompt_execution_report_view", "billing_usage_report_view");
    }

    @Test
    void validSelectQueryPasses() {
        String sql = "SELECT job_id, status, created_at FROM render_jobs_report_view WHERE created_at >= '2025-01-01' LIMIT 100";
        SqlSafetyResult result = validator.validate(sql, allowedDatasets);
        assertTrue(result.safe());
        assertTrue(result.violations().isEmpty());
    }

    @Test
    void rejectsNonSelectStatement() {
        String sql = "DELETE FROM render_jobs_report_view WHERE job_id = '123'";
        SqlSafetyResult result = validator.validate(sql, allowedDatasets);
        assertFalse(result.safe());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("SELECT")));
    }

    @Test
    void rejectsDdlStatements() {
        String sql = "CREATE TABLE test_table (id INT)";
        SqlSafetyResult result = validator.validate(sql, allowedDatasets);
        assertFalse(result.safe());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("DDL")));
    }

    @Test
    void rejectsInsertStatements() {
        String sql = "INSERT INTO render_jobs_report_view VALUES ('1', 'done')";
        SqlSafetyResult result = validator.validate(sql, allowedDatasets);
        assertFalse(result.safe());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("DML")));
    }

    @Test
    void rejectsUpdateStatements() {
        String sql = "UPDATE render_jobs_report_view SET status = 'done'";
        SqlSafetyResult result = validator.validate(sql, allowedDatasets);
        assertFalse(result.safe());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("DML")));
    }

    @Test
    void rejectsSemicolonInjection() {
        String sql = "SELECT job_id FROM render_jobs_report_view LIMIT 100; DROP TABLE users";
        SqlSafetyResult result = validator.validate(sql, allowedDatasets);
        assertFalse(result.safe());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("semicolon")));
    }

    @Test
    void rejectsUnauthorizedDataset() {
        String sql = "SELECT id FROM unauthorized_table WHERE created_at >= '2025-01-01' LIMIT 100";
        SqlSafetyResult result = validator.validate(sql, allowedDatasets);
        assertFalse(result.safe());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("Unauthorized")));
    }

    @Test
    void rejectsSelectStar() {
        String sql = "SELECT * FROM render_jobs_report_view WHERE created_at >= '2025-01-01' LIMIT 100";
        SqlSafetyResult result = validator.validate(sql, allowedDatasets);
        assertFalse(result.safe());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("SELECT *")));
    }

    @Test
    void rejectsMissingLimit() {
        String sql = "SELECT job_id, status FROM render_jobs_report_view WHERE created_at >= '2025-01-01'";
        SqlSafetyResult result = validator.validate(sql, allowedDatasets);
        assertFalse(result.safe());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("LIMIT")));
    }

    @Test
    void rejectsCrossJoin() {
        String sql = "SELECT a.job_id FROM render_jobs_report_view CROSS JOIN artifact_report_view ON a.job_id = b.job_id WHERE a.created_at >= '2025-01-01' LIMIT 100";
        SqlSafetyResult result = validator.validate(sql, allowedDatasets);
        assertFalse(result.safe());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("CROSS JOIN")));
    }

    @Test
    void requiresTimeRangeForTimeSeries() {
        String sql = "SELECT job_id, status FROM render_jobs_report_view LIMIT 100";
        SqlSafetyResult result = validator.validate(sql, allowedDatasets);
        assertFalse(result.safe());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("time range")));
    }

    @Test
    void rejectsSensitiveFields() {
        String sql = "SELECT api_key, password FROM render_jobs_report_view WHERE created_at >= '2025-01-01' LIMIT 100";
        SqlSafetyResult result = validator.validate(sql, allowedDatasets);
        assertFalse(result.safe());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("Sensitive")));
    }

    @Test
    void normalizesSql() {
        String sql = "SELECT  job_id,   status  FROM render_jobs_report_view WHERE created_at >= '2025-01-01' LIMIT 100";
        SqlSafetyResult result = validator.validate(sql, allowedDatasets);
        assertNotNull(result.normalizedSql());
        assertFalse(result.normalizedSql().contains("  "));
    }

    @Test
    void extractsReferencedDatasets() {
        String sql = "SELECT job_id FROM render_jobs_report_view WHERE created_at >= '2025-01-01' LIMIT 100";
        SqlSafetyResult result = validator.validate(sql, allowedDatasets);
        assertTrue(result.referencedDatasets().contains("render_jobs_report_view"));
    }

    @Test
    void extractsReferencedFields() {
        String sql = "SELECT job_id, status, created_at FROM render_jobs_report_view WHERE created_at >= '2025-01-01' LIMIT 100";
        SqlSafetyResult result = validator.validate(sql, allowedDatasets);
        assertTrue(result.referencedFields().contains("job_id"));
        assertTrue(result.referencedFields().contains("status"));
    }

    @Test
    void setsRiskLevelLowForNoViolations() {
        String sql = "SELECT job_id, status FROM render_jobs_report_view WHERE created_at >= '2025-01-01' LIMIT 100";
        SqlSafetyResult result = validator.validate(sql, allowedDatasets);
        assertEquals("LOW", result.estimatedRisk());
    }

    @Test
    void setsRiskLevelHighForMultipleViolations() {
        String sql = "SELECT * FROM unauthorized_table; DROP TABLE users";
        SqlSafetyResult result = validator.validate(sql, allowedDatasets);
        assertTrue(result.estimatedRisk().equals("HIGH") || result.estimatedRisk().equals("CRITICAL"));
    }

    @Test
    void requiresReviewForMultipleViolations() {
        String sql = "SELECT * FROM unauthorized_table; DROP TABLE users";
        SqlSafetyResult result = validator.validate(sql, allowedDatasets);
        assertTrue(result.requiresReview());
    }

    @Test
    void withClausePassesWithLimit() {
        Set<String> ctAllowed = new java.util.LinkedHashSet<>(allowedDatasets);
        ctAllowed.add("recent_jobs");
        String sql = "WITH recent_jobs AS (SELECT job_id, status FROM render_jobs_report_view WHERE created_at >= '2025-01-01' LIMIT 100) SELECT job_id, status FROM recent_jobs LIMIT 100";
        SqlSafetyResult result = validator.validate(sql, ctAllowed);
        assertTrue(result.safe());
    }

    @Test
    void handlesNullSql() {
        SqlSafetyResult result = validator.validate(null, allowedDatasets);
        assertFalse(result.safe());
    }
}
