package com.example.platform.federation.nlq.app;

import com.example.platform.federation.nlq.domain.QueryCostEstimate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SqlCostEstimatorTest {

    private SqlCostEstimator estimator;

    @BeforeEach
    void setUp() {
        estimator = new SqlCostEstimator();
    }

    @Test
    void lowRiskForSimpleQuery() {
        String sql = "SELECT job_id FROM render_jobs_report_view WHERE created_at >= CURRENT_DATE - INTERVAL '7 days' LIMIT 100";
        QueryCostEstimate estimate = estimator.estimate(sql);
        assertEquals("LOW", estimate.riskLevel());
        assertFalse(estimate.requiresReview());
    }

    @Test
    void mediumRiskForModerateQuery() {
        String sql = "SELECT status, COUNT(*) AS cnt FROM render_jobs_report_view WHERE created_at >= CURRENT_DATE - INTERVAL '60 days' GROUP BY status LIMIT 500";
        QueryCostEstimate estimate = estimator.estimate(sql);
        assertEquals("MEDIUM", estimate.riskLevel());
        assertFalse(estimate.requiresReview());
    }

    @Test
    void highRiskForLargeLookback() {
        String sql = "SELECT job_id FROM render_jobs_report_view WHERE created_at >= CURRENT_DATE - INTERVAL '400 days' LIMIT 50000";
        QueryCostEstimate estimate = estimator.estimate(sql);
        assertTrue(estimate.riskLevel().equals("HIGH") || estimate.riskLevel().equals("CRITICAL"));
        assertTrue(estimate.requiresReview());
    }

    @Test
    void criticalRiskForVeryExpensiveQuery() {
        String sql = "SELECT a.job_id FROM render_jobs_report_view JOIN artifact_report_view ON a.job_id = b.job_id WHERE a.created_at >= CURRENT_DATE - INTERVAL '400 days' GROUP BY a.job_id ORDER BY a.created_at LIMIT 50000";
        QueryCostEstimate estimate = estimator.estimate(sql);
        assertEquals("CRITICAL", estimate.riskLevel());
        assertTrue(estimate.requiresReview());
    }

    @Test
    void estimatesDatasetCount() {
        String sql = "SELECT a.job_id FROM render_jobs_report_view JOIN artifact_report_view ON a.job_id = b.job_id LIMIT 100";
        QueryCostEstimate estimate = estimator.estimate(sql);
        assertEquals(2, estimate.datasetCount());
    }

    @Test
    void estimatesSingleDataset() {
        String sql = "SELECT job_id FROM render_jobs_report_view LIMIT 100";
        QueryCostEstimate estimate = estimator.estimate(sql);
        assertEquals(1, estimate.datasetCount());
    }

    @Test
    void detectsGroupBy() {
        String sql = "SELECT status, COUNT(*) FROM render_jobs_report_view GROUP BY status LIMIT 100";
        QueryCostEstimate estimate = estimator.estimate(sql);
        assertTrue(estimate.hasGroupBy());
    }

    @Test
    void detectsOrderBy() {
        String sql = "SELECT job_id FROM render_jobs_report_view ORDER BY created_at LIMIT 100";
        QueryCostEstimate estimate = estimator.estimate(sql);
        assertTrue(estimate.hasOrderBy());
    }

    @Test
    void detectsJoin() {
        String sql = "SELECT a.job_id FROM render_jobs_report_view JOIN artifact_report_view ON a.job_id = b.job_id LIMIT 100";
        QueryCostEstimate estimate = estimator.estimate(sql);
        assertTrue(estimate.hasJoin());
    }

    @Test
    void extractsLimit() {
        String sql = "SELECT job_id FROM render_jobs_report_view LIMIT 250";
        QueryCostEstimate estimate = estimator.estimate(sql);
        assertEquals(250, estimate.limit());
    }

    @Test
    void extractsDaysRangeFromInterval() {
        String sql = "SELECT job_id FROM render_jobs_report_view WHERE created_at >= CURRENT_DATE - INTERVAL '30 days' LIMIT 100";
        QueryCostEstimate estimate = estimator.estimate(sql);
        assertEquals(30, estimate.daysRange());
    }

    @Test
    void requiresConfirmationForHighRisk() {
        QueryCostEstimate estimate = new QueryCostEstimate(1, 200, 100, false, false, false, "HIGH", true);
        assertTrue(estimator.requiresConfirmation(estimate));
    }

    @Test
    void requiresReviewForCritical() {
        QueryCostEstimate estimate = new QueryCostEstimate(3, 400, 50000, true, true, true, "CRITICAL", true);
        assertTrue(estimator.requiresReview(estimate));
    }

    @Test
    void noConfirmationForLowRisk() {
        QueryCostEstimate estimate = new QueryCostEstimate(1, 7, 100, false, false, false, "LOW", false);
        assertFalse(estimator.requiresConfirmation(estimate));
    }

    @Test
    void noReviewForLowRisk() {
        QueryCostEstimate estimate = new QueryCostEstimate(1, 7, 100, false, false, false, "LOW", false);
        assertFalse(estimator.requiresReview(estimate));
    }

    @Test
    void clampLimitReturnsDefaultForZero() {
        assertEquals(SqlCostEstimator.DEFAULT_ROWS, estimator.clampLimit(0));
    }

    @Test
    void clampLimitReturnsDefaultForNegative() {
        assertEquals(SqlCostEstimator.DEFAULT_ROWS, estimator.clampLimit(-1));
    }

    @Test
    void clampLimitCapsAtMax() {
        assertEquals(SqlCostEstimator.DEFAULT_MAX_ROWS, estimator.clampLimit(999999));
    }

    @Test
    void clampLimitPreservesValidValue() {
        assertEquals(500, estimator.clampLimit(500));
    }

    @Test
    void clampLookbackDaysCapsAtMax() {
        assertEquals(SqlCostEstimator.DEFAULT_MAX_LOOKBACK_DAYS, estimator.clampLookbackDays(999));
    }

    @Test
    void clampLookbackDaysReturnsDefaultForZero() {
        assertEquals(SqlCostEstimator.DEFAULT_MAX_LOOKBACK_DAYS, estimator.clampLookbackDays(0));
    }

    @Test
    void clampLookbackDaysPreservesValidValue() {
        assertEquals(30, estimator.clampLookbackDays(30));
    }

    @Test
    void timeoutForLowRisk() {
        QueryCostEstimate estimate = new QueryCostEstimate(1, 7, 100, false, false, false, "LOW", false);
        assertEquals(SqlCostEstimator.DEFAULT_TIMEOUT_SECONDS, estimator.getTimeoutSeconds(estimate));
    }

    @Test
    void timeoutForHighRisk() {
        QueryCostEstimate estimate = new QueryCostEstimate(1, 200, 100, false, false, false, "HIGH", true);
        assertEquals((int) (SqlCostEstimator.DEFAULT_TIMEOUT_SECONDS * 1.5), estimator.getTimeoutSeconds(estimate));
    }

    @Test
    void timeoutForCriticalRisk() {
        QueryCostEstimate estimate = new QueryCostEstimate(3, 400, 50000, true, true, true, "CRITICAL", true);
        assertEquals(SqlCostEstimator.DEFAULT_TIMEOUT_SECONDS * 2, estimator.getTimeoutSeconds(estimate));
    }

    @Test
    void defaultConstants() {
        assertEquals(1000, SqlCostEstimator.DEFAULT_MAX_ROWS);
        assertEquals(100, SqlCostEstimator.DEFAULT_ROWS);
        assertEquals(10, SqlCostEstimator.DEFAULT_TIMEOUT_SECONDS);
        assertEquals(90, SqlCostEstimator.DEFAULT_MAX_LOOKBACK_DAYS);
    }

    @Test
    void estimateWithExplicitParams() {
        String sql = "SELECT job_id FROM render_jobs_report_view LIMIT 100";
        QueryCostEstimate estimate = estimator.estimate(sql, 2, 45);
        assertEquals(2, estimate.datasetCount());
        assertEquals(45, estimate.daysRange());
        assertEquals(100, estimate.limit());
    }
}
