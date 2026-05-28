package com.example.platform.audit.app;

import com.example.platform.audit.domain.*;
import com.example.platform.shared.events.ProblematicDataDetectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Service for detecting problematic data across RenderJob, PromptExecution,
 * Provider, Worker, and KPI/SLA metrics.
 *
 * Detection covers:
 * - Bug-caused issues: missing fields, format errors, duplicates, invalid states
 * - Behavior anomalies: output mismatches, quality degradation, cost anomalies
 * - KPI/SLA breaches: latency spikes, error rate spikes, threshold exceeded
 */
@Service
public class ProblematicDataDetectionService {

    private static final Logger log = LoggerFactory.getLogger(ProblematicDataDetectionService.class);

    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final List<ProblematicDataRule> detectionRules;

    public ProblematicDataDetectionService(AuditService auditService,
            ApplicationEventPublisher eventPublisher) {
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.detectionRules = initializeRules();
    }

    private List<ProblematicDataRule> initializeRules() {
        return List.of(
                ProblematicDataRule.missingRenderJobOutput(),
                ProblematicDataRule.stuckRenderJob(),
                ProblematicDataRule.duplicateRenderJob(),
                ProblematicDataRule.promptSensitiveLeak(),
                ProblematicDataRule.promptOutputMismatch(),
                ProblematicDataRule.providerErrorSpike(),
                ProblematicDataRule.workerStaleHeartbeat(),
                ProblematicDataRule.slaBreach(),
                ProblematicDataRule.costAnomaly(),
                ProblematicDataRule.danglingTimelineAsset(),
                ProblematicDataRule.orphanArtifactBlob(),
                ProblematicDataRule.unresolvedAssetUri(),
                ProblematicDataRule.missingArtifactBlob(),
                ProblematicDataRule.storageBucketOrphan()
        );
    }

    /**
     * Records asset/timeline integrity findings from {@code AssetIntegrityScanService}.
     */
    public List<ProblematicDataRecord> detectAssetIntegrityFindings(
            String projectId, String tenantId, List<Map<String, Object>> findings) {
        List<ProblematicDataRecord> detected = new ArrayList<>();
        for (Map<String, Object> finding : findings) {
            String ruleId = String.valueOf(finding.getOrDefault("ruleId", "AST-001"));
            String dataId = String.valueOf(finding.getOrDefault("dataId", projectId));
            String description = String.valueOf(finding.getOrDefault("message", "Asset integrity issue"));
            ProblematicSeverity severity = severityForAssetRule(ruleId);
            ProblematicDataType type = typeForAssetRule(ruleId);
            detected.add(createRecord(dataId, "ASSET_INTEGRITY", tenantId, null,
                    type, severity, ruleId, description, finding, projectId, null, null));
        }
        for (ProblematicDataRecord record : detected) {
            eventPublisher.publishEvent(new ProblematicDataDetectedEvent(
                    record.recordId(), record.dataType(), record.dataId(),
                    record.problematicType().name(), record.severity().name(),
                    record.detectionRule(), record.description(),
                    record.context(), record.detectedAt().toInstant()));
            auditService.record("SYSTEM", "problematic-data-detector", "PROBLEMATIC_DATA_DETECTED",
                    "problematic_data", record.recordId(), Map.of(
                            "dataType", record.dataType(),
                            "severity", record.severity().name(),
                            "rule", record.detectionRule()));
        }
        return detected;
    }

    private static ProblematicSeverity severityForAssetRule(String ruleId) {
        return switch (ruleId) {
            case "AST-002", "AST-005" -> ProblematicSeverity.MEDIUM;
            default -> ProblematicSeverity.HIGH;
        };
    }

    private static ProblematicDataType typeForAssetRule(String ruleId) {
        return switch (ruleId) {
            case "AST-002", "AST-005" -> ProblematicDataType.LOGIC_CONFLICT;
            default -> ProblematicDataType.MISSING_FIELD;
        };
    }

    /**
     * Detect problematic data for a RenderJob.
     */
    public List<ProblematicDataRecord> detectRenderJobIssues(String renderJobId, String tenantId,
            String userId, Map<String, Object> renderJobData) {
        List<ProblematicDataRecord> detected = new ArrayList<>();

        // Check for missing output
        if (isCompletedWithoutOutput(renderJobData)) {
            detected.add(createRecord(renderJobId, "RENDER_JOB", tenantId, userId,
                    ProblematicDataType.MISSING_FIELD, ProblematicSeverity.HIGH,
                    "RJB-001", "RenderJob completed but has no output artifact",
                    renderJobData, null, null, null));
        }

        // Check for stuck job
        if (isStuckJob(renderJobData)) {
            detected.add(createRecord(renderJobId, "RENDER_JOB", tenantId, userId,
                    ProblematicDataType.INVALID_STATE_TRANSITION, ProblematicSeverity.MEDIUM,
                    "RJB-002", "RenderJob stuck in non-terminal state for too long",
                    renderJobData, null, null, null));
        }

        // Check for cost anomaly
        if (isCostAnomaly(renderJobData)) {
            detected.add(createRecord(renderJobId, "RENDER_JOB", tenantId, userId,
                    ProblematicDataType.COST_ANOMALY, ProblematicSeverity.HIGH,
                    "CST-001", "Render job cost significantly exceeds estimated cost",
                    renderJobData, null, null, null));
        }

        // Check for SLA breach
        if (isSlaBreach(renderJobData)) {
            detected.add(createRecord(renderJobId, "RENDER_JOB", tenantId, userId,
                    ProblematicDataType.SLA_BREACH, ProblematicSeverity.CRITICAL,
                    "SLA-001", "Render job exceeded SLA time limit",
                    renderJobData, null, null, null));
        }

        // Check for quality degradation
        if (isQualityDegradation(renderJobData)) {
            detected.add(createRecord(renderJobId, "RENDER_JOB", tenantId, userId,
                    ProblematicDataType.QUALITY_DEGRADATION, ProblematicSeverity.HIGH,
                    "QLT-001", "Rendered output quality below expected threshold",
                    renderJobData, null, null, null));
        }

        // Publish events and audit
        for (ProblematicDataRecord record : detected) {
            eventPublisher.publishEvent(new ProblematicDataDetectedEvent(
                    record.recordId(), record.dataType(), record.dataId(),
                    record.problematicType().name(), record.severity().name(),
                    record.detectionRule(), record.description(),
                    record.context(), record.detectedAt().toInstant()));
            auditService.record("SYSTEM", "problematic-data-detector", "PROBLEMATIC_DATA_DETECTED",
                    "problematic_data", record.recordId(), Map.of(
                            "dataType", record.dataType(),
                            "severity", record.severity().name(),
                            "rule", record.detectionRule()));
        }

        return detected;
    }

    /**
     * Detect problematic data for a PromptExecution.
     */
    public List<ProblematicDataRecord> detectPromptExecutionIssues(String executionId, String tenantId,
            String userId, Map<String, Object> executionData) {
        List<ProblematicDataRecord> detected = new ArrayList<>();

        // Check for sensitive data leak
        if (containsSensitiveDataLeak(executionData)) {
            detected.add(createRecord(executionId, "PROMPT_EXECUTION", tenantId, userId,
                    ProblematicDataType.MISSING_FIELD, ProblematicSeverity.CRITICAL,
                    "PMT-001", "Sensitive prompt variable found in execution record",
                    executionData, null, executionId, null));
        }

        // Check for output mismatch
        if (isPromptOutputMismatch(executionData)) {
            detected.add(createRecord(executionId, "PROMPT_EXECUTION", tenantId, userId,
                    ProblematicDataType.OUTPUT_MISMATCH, ProblematicSeverity.HIGH,
                    "PMT-002", "Prompt execution output does not match expected format",
                    executionData, null, executionId, null));
        }

        // Check for risk level escalation
        if (isRiskEscalation(executionData)) {
            detected.add(createRecord(executionId, "PROMPT_EXECUTION", tenantId, userId,
                    ProblematicDataType.LOGIC_CONFLICT, ProblematicSeverity.HIGH,
                    "PMT-003", "Prompt risk level escalated after execution",
                    executionData, null, executionId, null));
        }

        for (ProblematicDataRecord record : detected) {
            eventPublisher.publishEvent(new ProblematicDataDetectedEvent(
                    record.recordId(), record.dataType(), record.dataId(),
                    record.problematicType().name(), record.severity().name(),
                    record.detectionRule(), record.description(),
                    record.context(), record.detectedAt().toInstant()));
            auditService.record("SYSTEM", "problematic-data-detector", "PROBLEMATIC_DATA_DETECTED",
                    "problematic_data", record.recordId(), Map.of(
                            "dataType", record.dataType(),
                            "severity", record.severity().name(),
                            "rule", record.detectionRule()));
        }

        return detected;
    }

    /**
     * Detect provider/worker health anomalies.
     */
    public List<ProblematicDataRecord> detectProviderWorkerIssues(String providerKey, String workerId,
            String tenantId, Map<String, Object> healthData) {
        List<ProblematicDataRecord> detected = new ArrayList<>();

        // Check for error rate spike
        if (isProviderErrorSpike(healthData)) {
            String dataId = providerKey != null ? providerKey : workerId;
            detected.add(createRecord(dataId, "PROVIDER_WORKER", tenantId, null,
                    ProblematicDataType.ERROR_RATE_SPIKE, ProblematicSeverity.HIGH,
                    "PRV-001", "Provider/Worker error rate exceeds threshold",
                    healthData, null, null, workerId));
        }

        // Check for stale heartbeat
        if (isWorkerStaleHeartbeat(healthData)) {
            detected.add(createRecord(workerId, "PROVIDER_WORKER", tenantId, null,
                    ProblematicDataType.PERFORMANCE_ANOMALY, ProblematicSeverity.MEDIUM,
                    "WRK-001", "Worker heartbeat is stale",
                    healthData, null, null, workerId));
        }

        for (ProblematicDataRecord record : detected) {
            eventPublisher.publishEvent(new ProblematicDataDetectedEvent(
                    record.recordId(), record.dataType(), record.dataId(),
                    record.problematicType().name(), record.severity().name(),
                    record.detectionRule(), record.description(),
                    record.context(), record.detectedAt().toInstant()));
            auditService.record("SYSTEM", "problematic-data-detector", "PROBLEMATIC_DATA_DETECTED",
                    "problematic_data", record.recordId(), Map.of(
                            "dataType", record.dataType(),
                            "severity", record.severity().name(),
                            "rule", record.detectionRule()));
        }

        return detected;
    }

    /**
     * Get all active detection rules.
     */
    public List<ProblematicDataRule> getActiveRules() {
        return detectionRules.stream()
                .filter(ProblematicDataRule::enabled)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Detection helpers
    // -------------------------------------------------------------------------

    private boolean isCompletedWithoutOutput(Map<String, Object> data) {
        String status = (String) data.get("status");
        Integer artifactCount = (Integer) data.get("artifactCount");
        return "COMPLETED".equals(status) && (artifactCount == null || artifactCount == 0);
    }

    private boolean isStuckJob(Map<String, Object> data) {
        String status = (String) data.get("status");
        Long minutesInState = (Long) data.get("minutesInState");
        return ("PROCESSING".equals(status) || "RENDERING".equals(status))
                && minutesInState != null && minutesInState > 30;
    }

    private boolean isCostAnomaly(Map<String, Object> data) {
        Double actualCost = toDouble(data.get("actualCost"));
        Double estimatedCost = toDouble(data.get("estimatedCost"));
        return actualCost != null && estimatedCost != null && estimatedCost > 0
                && actualCost > estimatedCost * 2;
    }

    private boolean isSlaBreach(Map<String, Object> data) {
        Long processingTime = toLong(data.get("processingTimeSeconds"));
        Long slaThreshold = toLong(data.get("slaThresholdSeconds"));
        String status = (String) data.get("status");
        return "COMPLETED".equals(status) && processingTime != null && slaThreshold != null
                && processingTime > slaThreshold;
    }

    private boolean isQualityDegradation(Map<String, Object> data) {
        Integer qualityScore = (Integer) data.get("qualityScore");
        return qualityScore != null && qualityScore < 50;
    }

    private boolean containsSensitiveDataLeak(Map<String, Object> data) {
        String redactedJson = (String) data.get("inputVariablesRedactedJson");
        if (redactedJson == null) return false;
        String lower = redactedJson.toLowerCase();
        return (lower.contains("password") || lower.contains("api_key") || lower.contains("secret"))
                && !lower.contains("[redacted]");
    }

    private boolean isPromptOutputMismatch(Map<String, Object> data) {
        String status = (String) data.get("status");
        String output = (String) data.get("outputSummary");
        return "SUCCEEDED".equals(status) && (output == null || output.isBlank());
    }

    private boolean isRiskEscalation(Map<String, Object> data) {
        String preRisk = (String) data.get("preExecutionRiskLevel");
        String postRisk = (String) data.get("postExecutionRiskLevel");
        return preRisk != null && postRisk != null
                && ProblematicSeverity.valueOf(postRisk).ordinal() > ProblematicSeverity.valueOf(preRisk).ordinal();
    }

    private boolean isProviderErrorSpike(Map<String, Object> data) {
        Double errorRate = toDouble(data.get("errorRate"));
        return errorRate != null && errorRate > 0.2;
    }

    private boolean isWorkerStaleHeartbeat(Map<String, Object> data) {
        Long minutesSinceHeartbeat = toLong(data.get("minutesSinceLastHeartbeat"));
        return minutesSinceHeartbeat != null && minutesSinceHeartbeat > 5;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ProblematicDataRecord createRecord(String dataId, String dataType, String tenantId,
            String userId, ProblematicDataType problematicType, ProblematicSeverity severity,
            String ruleId, String description, Map<String, Object> context,
            String sessionId, String promptExecutionId, String workerId) {
        return new ProblematicDataRecord(
                "pd-" + UUID.randomUUID().toString().substring(0, 8),
                dataType, dataId, tenantId, userId,
                problematicType, severity, ruleId, description,
                context != null ? Map.copyOf(context) : Map.of(),
                sessionId, null, promptExecutionId,
                null, workerId,
                ProblematicDataStatus.DETECTED,
                null, null,
                isHumanReviewRequired(severity, problematicType),
                null, OffsetDateTime.now(), null, null);
    }

    private boolean isHumanReviewRequired(ProblematicSeverity severity, ProblematicDataType type) {
        return severity == ProblematicSeverity.CRITICAL
                || severity == ProblematicSeverity.HIGH
                || type == ProblematicDataType.SLA_BREACH
                || type == ProblematicDataType.MISSING_FIELD
                || type == ProblematicDataType.OUTPUT_MISMATCH;
    }

    private Double toDouble(Object value) {
        if (value instanceof Double d) return d;
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value instanceof Long l) return l;
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
