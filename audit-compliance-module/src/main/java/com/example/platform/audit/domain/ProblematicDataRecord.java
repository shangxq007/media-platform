package com.example.platform.audit.domain;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Record representing a detected problematic data entry.
 * Can be caused by system bugs or behavior anomalies.
 */
public record ProblematicDataRecord(
        String recordId,
        String dataType,
        String dataId,
        String tenantId,
        String userId,
        ProblematicDataType problematicType,
        ProblematicSeverity severity,
        String detectionRule,
        String description,
        Map<String, Object> context,
        String sourceSessionId,
        String renderJobId,
        String promptExecutionId,
        String providerKey,
        String workerId,
        ProblematicDataStatus status,
        String autoFixApplied,
        String quarantineTable,
        boolean requiresHumanReview,
        String humanReviewNotes,
        OffsetDateTime detectedAt,
        OffsetDateTime resolvedAt,
        String resolvedBy
) {
    public ProblematicDataRecord withStatus(ProblematicDataStatus newStatus) {
        return new ProblematicDataRecord(recordId, dataType, dataId, tenantId, userId,
                problematicType, severity, detectionRule, description, context,
                sourceSessionId, renderJobId, promptExecutionId, providerKey, workerId,
                newStatus, autoFixApplied, quarantineTable, requiresHumanReview,
                humanReviewNotes, detectedAt, resolvedAt, resolvedBy);
    }

    public ProblematicDataRecord withAutoFix(String fixApplied) {
        return new ProblematicDataRecord(recordId, dataType, dataId, tenantId, userId,
                problematicType, severity, detectionRule, description, context,
                sourceSessionId, renderJobId, promptExecutionId, providerKey, workerId,
                status, fixApplied, quarantineTable, requiresHumanReview,
                humanReviewNotes, detectedAt, resolvedAt, resolvedBy);
    }

    public ProblematicDataRecord withHumanReview(String notes, boolean requiresReview) {
        return new ProblematicDataRecord(recordId, dataType, dataId, tenantId, userId,
                problematicType, severity, detectionRule, description, context,
                sourceSessionId, renderJobId, promptExecutionId, providerKey, workerId,
                status, autoFixApplied, quarantineTable, requiresReview,
                notes, detectedAt, resolvedAt, resolvedBy);
    }

    public ProblematicDataRecord resolved(String resolvedBy) {
        return new ProblematicDataRecord(recordId, dataType, dataId, tenantId, userId,
                problematicType, severity, detectionRule, description, context,
                sourceSessionId, renderJobId, promptExecutionId, providerKey, workerId,
                ProblematicDataStatus.RESOLVED, autoFixApplied, quarantineTable,
                requiresHumanReview, humanReviewNotes, detectedAt, OffsetDateTime.now(), resolvedBy);
    }
}
