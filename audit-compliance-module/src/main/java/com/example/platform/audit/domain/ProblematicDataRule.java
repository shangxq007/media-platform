package com.example.platform.audit.domain;

import java.util.Map;

/**
 * Rule for detecting problematic data.
 */
public record ProblematicDataRule(
        String ruleId,
        String name,
        ProblematicDataType dataType,
        ProblematicSeverity defaultSeverity,
        String description,
        String detectionQuery,
        boolean autoFixable,
        String autoFixAction,
        boolean enabled
) {
    // RenderJob detection rules
    public static ProblematicDataRule missingRenderJobOutput() {
        return new ProblematicDataRule("RJB-001", "Missing RenderJob Output",
                ProblematicDataType.MISSING_FIELD, ProblematicSeverity.HIGH,
                "RenderJob completed but has no output artifact",
                "status = 'COMPLETED' AND artifact_count = 0", false, null, true);
    }

    public static ProblematicDataRule stuckRenderJob() {
        return new ProblematicDataRule("RJB-002", "Stuck RenderJob",
                ProblematicDataType.INVALID_STATE_TRANSITION, ProblematicSeverity.MEDIUM,
                "RenderJob stuck in non-terminal state for too long",
                "status IN ('PROCESSING', 'RENDERING') AND updated_at < NOW() - INTERVAL '30 minutes'",
                true, "MARK_STALE_AND_RETRY", true);
    }

    public static ProblematicDataRule duplicateRenderJob() {
        return new ProblematicDataRule("RJB-003", "Duplicate RenderJob",
                ProblematicDataType.DUPLICATE_ENTRY, ProblematicSeverity.LOW,
                "Multiple render jobs with same project+profile+timeline hash",
                "same_project_profile_timeline_count > 1", true, "MARK_DUPLICATE", true);
    }

    // Prompt detection rules
    public static ProblematicDataRule promptSensitiveLeak() {
        return new ProblematicDataRule("PMT-001", "Prompt Sensitive Data Leak",
                ProblematicDataType.MISSING_FIELD, ProblematicSeverity.CRITICAL,
                "Sensitive prompt variable found in execution record",
                "input_variables_redacted_json CONTAINS 'password' OR input_variables_redacted_json CONTAINS 'api_key'",
                false, null, true);
    }

    public static ProblematicDataRule promptOutputMismatch() {
        return new ProblematicDataRule("PMT-002", "Prompt Output Mismatch",
                ProblematicDataType.OUTPUT_MISMATCH, ProblematicSeverity.HIGH,
                "Prompt execution output does not match expected format",
                "status = 'SUCCEEDED' AND output_summary IS NULL",
                false, null, true);
    }

    // Provider/Worker detection rules
    public static ProblematicDataRule providerErrorSpike() {
        return new ProblematicDataRule("PRV-001", "Provider Error Spike",
                ProblematicDataType.ERROR_RATE_SPIKE, ProblematicSeverity.HIGH,
                "Provider error rate exceeds threshold in time window",
                "provider_error_rate > 0.2", false, null, true);
    }

    public static ProblematicDataRule workerStaleHeartbeat() {
        return new ProblematicDataRule("WRK-001", "Worker Stale Heartbeat",
                ProblematicDataType.PERFORMANCE_ANOMALY, ProblematicSeverity.MEDIUM,
                "Remote worker has not sent heartbeat within expected interval",
                "worker_last_heartbeat < NOW() - INTERVAL '5 minutes'",
                true, "MARK_WORKER_OFFLINE", true);
    }

    // KPI/SLA detection rules
    public static ProblematicDataRule slaBreach() {
        return new ProblematicDataRule("SLA-001", "SLA Breach",
                ProblematicDataType.SLA_BREACH, ProblematicSeverity.CRITICAL,
                "Render job exceeded SLA time limit",
                "status = 'COMPLETED' AND processing_time_seconds > sla_threshold",
                false, null, true);
    }

    public static ProblematicDataRule costAnomaly() {
        return new ProblematicDataRule("CST-001", "Cost Anomaly",
                ProblematicDataType.COST_ANOMALY, ProblematicSeverity.HIGH,
                "Render job cost significantly exceeds estimated cost",
                "actual_cost > estimated_cost * 2", false, null, true);
    }

    public static ProblematicDataRule danglingTimelineAsset() {
        return new ProblematicDataRule("AST-001", "Dangling Timeline Asset Reference",
                ProblematicDataType.MISSING_FIELD, ProblematicSeverity.HIGH,
                "Timeline clip references missing or tombstoned assetId",
                "clip.assetId NOT IN assetRegistry OR registry.status = TOMBSTONED", false, null, true);
    }

    public static ProblematicDataRule orphanArtifactBlob() {
        return new ProblematicDataRule("AST-002", "Orphan Artifact Blob",
                ProblematicDataType.LOGIC_CONFLICT, ProblematicSeverity.MEDIUM,
                "Tombstoned catalog artifact still has storage object",
                "artifact.status = TOMBSTONED AND blob.exists = true", true, "RUN_ARTIFACT_GC", true);
    }

    public static ProblematicDataRule unresolvedAssetUri() {
        return new ProblematicDataRule("AST-003", "Unresolved Asset URI",
                ProblematicDataType.MISSING_FIELD, ProblematicSeverity.HIGH,
                "assetRegistry entry lacks concrete storage URI",
                "asset.uri IS NULL OR asset.uri LIKE 'asset://%'", false, null, true);
    }

    public static ProblematicDataRule missingArtifactBlob() {
        return new ProblematicDataRule("AST-004", "Missing Artifact Blob",
                ProblematicDataType.MISSING_FIELD, ProblematicSeverity.HIGH,
                "Active catalog artifact storage object not found",
                "artifact.status = ACTIVE AND blob.exists = false", false, null, true);
    }

    public static ProblematicDataRule storageBucketOrphan() {
        return new ProblematicDataRule("AST-005", "Storage Bucket Orphan Object",
                ProblematicDataType.LOGIC_CONFLICT, ProblematicSeverity.MEDIUM,
                "Object exists in storage bucket but is not referenced by catalog, timeline, render, or delivery",
                "bucket_object NOT IN known_uri_index", false, null, true);
    }
}
