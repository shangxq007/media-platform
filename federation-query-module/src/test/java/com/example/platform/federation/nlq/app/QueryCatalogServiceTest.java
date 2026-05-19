package com.example.platform.federation.nlq.app;

import com.example.platform.federation.nlq.domain.QueryDataset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class QueryCatalogServiceTest {

    private QueryCatalogService service;

    @BeforeEach
    void setUp() {
        service = new QueryCatalogService();
    }

    @Test
    void listsAllDefaultDatasets() {
        List<QueryDataset> datasets = service.listDatasets();
        assertEquals(14, datasets.size());
    }

    @Test
    void containsRenderJobsDataset() {
        Optional<QueryDataset> ds = service.getDataset("render_jobs_report_view");
        assertTrue(ds.isPresent());
        assertEquals("Render Jobs Report", ds.get().name());
        assertTrue(ds.get().enabled());
        assertTrue(ds.get().tenantScoped());
    }

    @Test
    void containsArtifactDataset() {
        Optional<QueryDataset> ds = service.getDataset("artifact_report_view");
        assertTrue(ds.isPresent());
        assertEquals("medium", ds.get().sensitivityLevel());
    }

    @Test
    void containsPromptExecutionDataset() {
        Optional<QueryDataset> ds = service.getDataset("prompt_execution_report_view");
        assertTrue(ds.isPresent());
        assertTrue(ds.get().userScoped());
    }

    @Test
    void containsBillingUsageDataset() {
        Optional<QueryDataset> ds = service.getDataset("billing_usage_report_view");
        assertTrue(ds.isPresent());
        assertEquals(365, ds.get().maxLookbackDays());
        assertEquals("high", ds.get().sensitivityLevel());
    }

    @Test
    void containsEntitlementDecisionDataset() {
        Optional<QueryDataset> ds = service.getDataset("entitlement_decision_report_view");
        assertTrue(ds.isPresent());
        assertTrue(ds.get().userScoped());
    }

    @Test
    void containsQuotaUsageDataset() {
        Optional<QueryDataset> ds = service.getDataset("quota_usage_report_view");
        assertTrue(ds.isPresent());
        assertEquals("low", ds.get().sensitivityLevel());
    }

    @Test
    void containsFeedbackDataset() {
        Optional<QueryDataset> ds = service.getDataset("feedback_report_view");
        assertTrue(ds.isPresent());
        assertTrue(ds.get().userScoped());
    }

    @Test
    void containsProviderHealthDataset() {
        Optional<QueryDataset> ds = service.getDataset("provider_health_report_view");
        assertTrue(ds.isPresent());
        assertFalse(ds.get().workspaceScoped());
    }

    @Test
    void containsWorkerStatusDataset() {
        Optional<QueryDataset> ds = service.getDataset("worker_status_report_view");
        assertTrue(ds.isPresent());
        assertEquals(7, ds.get().maxLookbackDays());
    }

    @Test
    void containsFeatureFlagDataset() {
        Optional<QueryDataset> ds = service.getDataset("feature_flag_evaluation_report_view");
        assertTrue(ds.isPresent());
        assertTrue(ds.get().userScoped());
    }

    @Test
    void containsExtensionExecutionDataset() {
        Optional<QueryDataset> ds = service.getDataset("extension_execution_report_view");
        assertTrue(ds.isPresent());
    }

    @Test
    void containsProblematicDataDataset() {
        Optional<QueryDataset> ds = service.getDataset("problematic_data_report_view");
        assertTrue(ds.isPresent());
    }

    @Test
    void containsAuditEventDataset() {
        Optional<QueryDataset> ds = service.getDataset("audit_event_report_view");
        assertTrue(ds.isPresent());
        assertEquals("audit", ds.get().module());
    }

    @Test
    void getDatasetReturnsEmptyForUnknown() {
        Optional<QueryDataset> ds = service.getDataset("nonexistent_view");
        assertTrue(ds.isEmpty());
    }

    @Test
    void registerDatasetAddsNewDataset() {
        QueryDataset custom = new QueryDataset(
            "custom_view", "Custom", "Test", "custom_view", "test", "test", true,
            "created_at", true, false, false,
            List.of("admin"), List.of("test.read"),
            500, 30, "low",
            Instant.now(), Instant.now()
        );
        service.registerDataset(custom);
        assertTrue(service.getDataset("custom_view").isPresent());
        assertEquals(15, service.listDatasets().size());
    }

    @Test
    void disableDatasetSetsEnabledFalse() {
        Optional<QueryDataset> disabled = service.disableDataset("render_jobs_report_view");
        assertTrue(disabled.isPresent());
        assertFalse(disabled.get().enabled());
    }

    @Test
    void enableDatasetSetsEnabledTrue() {
        service.disableDataset("render_jobs_report_view");
        Optional<QueryDataset> enabled = service.enableDataset("render_jobs_report_view");
        assertTrue(enabled.isPresent());
        assertTrue(enabled.get().enabled());
    }

    @Test
    void disableDatasetReturnsEmptyForUnknown() {
        Optional<QueryDataset> result = service.disableDataset("nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    void enableDatasetReturnsEmptyForUnknown() {
        Optional<QueryDataset> result = service.enableDataset("nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    void listDatasetsFiltersByPermissions() {
        List<QueryDataset> datasets = service.listDatasets(
            "user1", "tenant1", "ws1",
            List.of("admin"),
            List.of("analytics.read"),
            List.of(),
            List.of()
        );
        assertFalse(datasets.isEmpty());
    }

    @Test
    void listDatasetsFiltersOutDisabled() {
        service.disableDataset("render_jobs_report_view");
        List<QueryDataset> datasets = service.listDatasets(
            "user1", "tenant1", "ws1",
            List.of("admin"),
            List.of("analytics.read"),
            List.of(),
            List.of()
        );
        assertTrue(datasets.stream().noneMatch(d -> d.datasetKey().equals("render_jobs_report_view")));
    }

    @Test
    void listDatasetsFiltersByRoles() {
        List<QueryDataset> datasets = service.listDatasets(
            "user1", "tenant1", "ws1",
            List.of("auditor"),
            List.of("audit.read"),
            List.of(),
            List.of()
        );
        assertTrue(datasets.stream().allMatch(d ->
            d.allowedRoles().contains("auditor")));
    }

    @Test
    void listDatasetsReturnsEmptyForNoMatchingRoles() {
        List<QueryDataset> datasets = service.listDatasets(
            "user1", "tenant1", "ws1",
            List.of("nonexistent_role"),
            List.of("nonexistent.read"),
            List.of(),
            List.of()
        );
        assertTrue(datasets.isEmpty());
    }

    @Test
    void datasetHasCorrectDefaultTimeField() {
        assertEquals("usage_date", service.getDataset("billing_usage_report_view").get().defaultTimeField());
        assertEquals("evaluated_at", service.getDataset("entitlement_decision_report_view").get().defaultTimeField());
        assertEquals("checked_at", service.getDataset("provider_health_report_view").get().defaultTimeField());
        assertEquals("occurred_at", service.getDataset("audit_event_report_view").get().defaultTimeField());
        assertEquals("created_at", service.getDataset("render_jobs_report_view").get().defaultTimeField());
    }
}
