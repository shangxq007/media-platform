package com.example.platform.federation.nlq.app;

import com.example.platform.federation.nlq.domain.QueryDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QueryCatalogService {

    private static final Logger log = LoggerFactory.getLogger(QueryCatalogService.class);

    private final Map<String, QueryDataset> datasets = new ConcurrentHashMap<>();

    public QueryCatalogService() {
        registerDefaultDatasets();
    }

    private void registerDefaultDatasets() {
        Instant now = Instant.now();

         registerDataset(new QueryDataset(
            "render_jobs_report_view", "Render Jobs Report",
            "Render job execution history with status, duration, and cost metrics",
            "render_jobs_report_view", "render", "system", true,
            "created_at", true, true, false,
            List.of("admin", "analyst", "developer"),
            List.of("render.read", "analytics.read"),
            1000, 90, "low",
            now, now
        ));

         registerDataset(new QueryDataset(
            "artifact_report_view", "Artifact Report",
            "Generated artifacts from render jobs including output URLs and metadata",
            "artifact_report_view", "render", "system", true,
            "created_at", true, true, false,
            List.of("admin", "analyst", "developer"),
            List.of("render.read", "analytics.read"),
            1000, 90, "medium",
            now, now
        ));

         registerDataset(new QueryDataset(
            "prompt_execution_report_view", "Prompt Execution Report",
            "AI prompt execution history with token usage and model details",
            "prompt_execution_report_view", "prompt", "system", true,
            "started_at", true, true, true,
            List.of("admin", "analyst"),
            List.of("prompt.read", "analytics.read"),
            1000, 90, "medium",
            now, now
        ));

         registerDataset(new QueryDataset(
            "billing_usage_report_view", "Billing Usage Report",
            "Billing and usage metrics for cost analysis and chargeback",
            "billing_usage_report_view", "billing", "system", true,
            "usage_date", true, true, false,
            List.of("admin", "billing_admin"),
            List.of("billing.read", "analytics.read"),
            1000, 365, "high",
            now, now
        ));

         registerDataset(new QueryDataset(
            "entitlement_decision_report_view", "Entitlement Decision Report",
            "Access control decisions and entitlement evaluation history",
            "entitlement_decision_report_view", "entitlement", "system", true,
            "evaluated_at", true, true, true,
            List.of("admin"),
            List.of("entitlement.read", "analytics.read"),
            500, 90, "high",
            now, now
        ));

         registerDataset(new QueryDataset(
            "quota_usage_report_view", "Quota Usage Report",
            "Resource quota consumption and limit tracking",
            "quota_usage_report_view", "entitlement", "system", true,
            "measured_at", true, true, false,
            List.of("admin", "analyst"),
            List.of("entitlement.read", "analytics.read"),
            1000, 90, "low",
            now, now
        ));

         registerDataset(new QueryDataset(
            "feedback_report_view", "Feedback Report",
            "User feedback and ratings for content quality analysis",
            "feedback_report_view", "user-analytics", "system", true,
            "submitted_at", true, true, true,
            List.of("admin", "analyst"),
            List.of("feedback.read", "analytics.read"),
            1000, 180, "medium",
            now, now
        ));

         registerDataset(new QueryDataset(
            "provider_health_report_view", "Provider Health Report",
            "Third-party provider health status and latency metrics",
            "provider_health_report_view", "render", "system", true,
            "checked_at", true, false, false,
            List.of("admin", "ops"),
            List.of("provider.read", "analytics.read"),
            500, 30, "low",
            now, now
        ));

         registerDataset(new QueryDataset(
            "worker_status_report_view", "Worker Status Report",
            "Worker node status, capacity, and task assignment metrics",
            "worker_status_report_view", "render", "system", true,
            "reported_at", true, false, false,
            List.of("admin", "ops"),
            List.of("render.read", "analytics.read"),
            500, 7, "low",
            now, now
        ));

         registerDataset(new QueryDataset(
            "feature_flag_evaluation_report_view", "Feature Flag Evaluation Report",
            "Feature flag evaluation history and variant distribution",
            "feature_flag_evaluation_report_view", "policy", "system", true,
            "evaluated_at", true, true, true,
            List.of("admin"),
            List.of("feature_flag.read", "analytics.read"),
            1000, 90, "low",
            now, now
        ));

         registerDataset(new QueryDataset(
            "extension_execution_report_view", "Extension Execution Report",
            "Extension and plugin execution history with performance metrics",
            "extension_execution_report_view", "extension", "system", true,
            "executed_at", true, true, false,
            List.of("admin", "developer"),
            List.of("extension.read", "analytics.read"),
            1000, 90, "low",
            now, now
        ));

         registerDataset(new QueryDataset(
            "problematic_data_report_view", "Problematic Data Report",
            "Data quality issues, anomalies, and validation failures",
            "problematic_data_report_view", "user-analytics", "system", true,
            "detected_at", true, true, false,
            List.of("admin", "data_engineer"),
            List.of("analytics.read"),
            500, 90, "medium",
            now, now
        ));

         registerDataset(new QueryDataset(
            "audit_event_report_view", "Audit Event Report",
            "Audit trail of user and system actions for compliance",
            "audit_event_report_view", "audit", "system", true,
            "occurred_at", true, false, false,
            List.of("admin", "auditor"),
            List.of("audit.read"),
            1000, 365, "high",
            now, now
        ));

         registerDataset(new QueryDataset(
            "user_activity_report_view", "User Activity Report",
            "User login, session, and activity tracking for security monitoring",
            "user_activity_report_view", "identity-access", "system", true,
            "activity_at", true, false, true,
            List.of("admin", "security_admin"),
            List.of("identity.read", "analytics.read"),
            500, 90, "high",
            now, now
        ));

         log.info("QueryCatalogService: registered {} default datasets", datasets.size());
    }

    public List<QueryDataset> listDatasets() {
        return List.copyOf(datasets.values());
    }

    public List<QueryDataset> listDatasets(String userId, String tenantId, String workspaceId,
            List<String> roles, List<String> permissions, List<String> entitlements,
            List<String> featureFlags) {
        return datasets.values().stream()
            .filter(QueryDataset::enabled)
            .filter(ds -> roles != null && roles.stream().anyMatch(ds.allowedRoles()::contains))
            .filter(ds -> permissions != null && permissions.stream().anyMatch(ds.allowedPermissions()::contains))
            .toList();
    }

    public Optional<QueryDataset> getDataset(String key) {
        return Optional.ofNullable(datasets.get(key));
    }

    public QueryDataset registerDataset(QueryDataset dataset) {
        datasets.put(dataset.datasetKey(), dataset);
        log.info("QueryCatalogService: registered dataset {}", dataset.datasetKey());
        return dataset;
    }

    public Optional<QueryDataset> enableDataset(String key) {
        QueryDataset existing = datasets.get(key);
        if (existing == null) return Optional.empty();
        QueryDataset enabled = new QueryDataset(
            existing.datasetKey(), existing.name(), existing.description(),
            existing.viewName(), existing.module(), existing.owner(), true,
            existing.defaultTimeField(), existing.tenantScoped(), existing.workspaceScoped(),
            existing.userScoped(), existing.allowedRoles(), existing.allowedPermissions(),
            existing.maxRows(), existing.maxLookbackDays(), existing.sensitivityLevel(),
            existing.createdAt(), Instant.now()
        );
        datasets.put(key, enabled);
        log.info("QueryCatalogService: enabled dataset {}", key);
        return Optional.of(enabled);
    }

    public Optional<QueryDataset> disableDataset(String key) {
        QueryDataset existing = datasets.get(key);
        if (existing == null) return Optional.empty();
        QueryDataset disabled = new QueryDataset(
            existing.datasetKey(), existing.name(), existing.description(),
            existing.viewName(), existing.module(), existing.owner(), false,
            existing.defaultTimeField(), existing.tenantScoped(), existing.workspaceScoped(),
            existing.userScoped(), existing.allowedRoles(), existing.allowedPermissions(),
            existing.maxRows(), existing.maxLookbackDays(), existing.sensitivityLevel(),
            existing.createdAt(), Instant.now()
        );
        datasets.put(key, disabled);
        log.info("QueryCatalogService: disabled dataset {}", key);
        return Optional.of(disabled);
    }
}
