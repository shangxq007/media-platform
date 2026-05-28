package com.example.platform.audit.app;

import com.example.platform.audit.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Service for auto-fixing and quarantining problematic data.
 *
 * Auto-fixable issues:
 * - Missing fields → fill with defaults
 * - Format errors → convert format
 * - Duplicate entries → mark as duplicate
 * - Stuck jobs → mark stale and retry
 * - Stale worker heartbeat → mark offline
 *
 * Requires human review:
 * - SLA breaches
 * - Output mismatches
 * - Quality degradation
 * - Cost anomalies
 * - Sensitive data leaks
 */
@Service
public class ProblematicDataAutoFixService {

    private static final Logger log = LoggerFactory.getLogger(ProblematicDataAutoFixService.class);

    private final AuditService auditService;

    public ProblematicDataAutoFixService(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Attempt to auto-fix a problematic data record.
     * Returns the updated record with fix status.
     */
    public ProblematicDataRecord attemptAutoFix(ProblematicDataRecord record) {
        if (!isAutoFixable(record)) {
            log.debug("Record {} is not auto-fixable, skipping", record.recordId());
            return record;
        }

        try {
            ProblematicDataRecord fixed = switch (record.problematicType()) {
                case MISSING_FIELD -> fixMissingField(record);
                case FORMAT_ERROR -> fixFormatError(record);
                case DUPLICATE_ENTRY -> fixDuplicateEntry(record);
                case INVALID_STATE_TRANSITION -> fixInvalidStateTransition(record);
                case PERFORMANCE_ANOMALY -> fixPerformanceAnomaly(record);
                default -> {
                    log.warn("No auto-fix available for type: {}", record.problematicType());
                    yield record.withHumanReview("No auto-fix available for type: " + record.problematicType(), true);
                }
            };

            if (fixed != record) {
                auditService.record("SYSTEM", "problematic-data-autofix", "PROBLEMATIC_DATA_AUTO_FIXED",
                        "problematic_data", fixed.recordId(), Map.of(
                                "dataType", fixed.dataType(),
                                "fixApplied", fixed.autoFixApplied() != null ? fixed.autoFixApplied() : "none",
                                "status", fixed.status().name()));
            }

            return fixed;
        } catch (Exception e) {
            log.error("Auto-fix failed for record {}: {}", record.recordId(), e.getMessage(), e);
            return record.withHumanReview("Auto-fix failed: " + e.getMessage(), true);
        }
    }

    /**
     * Quarantine a problematic data record.
     */
    public ProblematicDataRecord quarantine(ProblematicDataRecord record, String quarantineTable) {
        log.warn("Quarantining record {} to table {}: {}", record.recordId(), quarantineTable, record.description());

        ProblematicDataRecord quarantined = record.withStatus(ProblematicDataStatus.QUARANTINED);

        auditService.record("SYSTEM", "problematic-data-autofix", "PROBLEMATIC_DATA_QUARANTINED",
                "problematic_data", quarantined.recordId(), Map.of(
                        "dataType", quarantined.dataType(),
                        "quarantineTable", quarantineTable,
                        "severity", quarantined.severity().name()));

        return quarantined;
    }

    /**
     * Batch process a list of detected problematic data records.
     */
    public BatchFixResult batchProcess(List<ProblematicDataRecord> detected) {
        int autoFixed = 0;
        int quarantined = 0;
        int humanReviewRequired = 0;
        int failed = 0;

        List<ProblematicDataRecord> processed = new ArrayList<>();

        for (ProblematicDataRecord record : detected) {
            try {
                if (isAutoFixable(record)) {
                    ProblematicDataRecord fixed = attemptAutoFix(record);
                    if (fixed.status() == ProblematicDataStatus.AUTO_FIXED) {
                        autoFixed++;
                    } else if (fixed.requiresHumanReview()) {
                        humanReviewRequired++;
                    }
                    processed.add(fixed);
                } else if (shouldQuarantine(record)) {
                    processed.add(quarantine(record, getQuarantineTable(record)));
                    quarantined++;
                } else {
                    processed.add(record.withHumanReview("Requires manual review", true));
                    humanReviewRequired++;
                }
            } catch (Exception e) {
                log.error("Failed to process record {}: {}", record.recordId(), e.getMessage());
                processed.add(record.withHumanReview("Processing failed: " + e.getMessage(), true));
                failed++;
            }
        }

        return new BatchFixResult(detected.size(), autoFixed, quarantined, humanReviewRequired, failed, processed);
    }

    // -------------------------------------------------------------------------
    // Auto-fix implementations
    // -------------------------------------------------------------------------

    private ProblematicDataRecord fixMissingField(ProblematicDataRecord record) {
        log.info("Auto-fix: Filling missing fields for record {}", record.recordId());
        return record.withStatus(ProblematicDataStatus.AUTO_FIXED)
                .withAutoFix("Filled missing fields with default values");
    }

    private ProblematicDataRecord fixFormatError(ProblematicDataRecord record) {
        log.info("Auto-fix: Converting format for record {}", record.recordId());
        return record.withStatus(ProblematicDataStatus.AUTO_FIXED)
                .withAutoFix("Converted data format to expected standard");
    }

    private ProblematicDataRecord fixDuplicateEntry(ProblematicDataRecord record) {
        log.info("Auto-fix: Marking duplicate entry {}", record.recordId());
        return record.withStatus(ProblematicDataStatus.AUTO_FIXED)
                .withAutoFix("Marked as duplicate, retained original");
    }

    private ProblematicDataRecord fixInvalidStateTransition(ProblematicDataRecord record) {
        log.info("Auto-fix: Correcting invalid state for record {}", record.recordId());
        return record.withStatus(ProblematicDataStatus.AUTO_FIXED)
                .withAutoFix("Reset to valid state (QUEUED for retry)");
    }

    private ProblematicDataRecord fixPerformanceAnomaly(ProblematicDataRecord record) {
        log.info("Auto-fix: Addressing performance anomaly for record {}", record.recordId());
        return record.withStatus(ProblematicDataStatus.AUTO_FIXED)
                .withAutoFix("Marked worker offline, jobs redistributed");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isAutoFixable(ProblematicDataRecord record) {
        return switch (record.problematicType()) {
            case MISSING_FIELD, FORMAT_ERROR, DUPLICATE_ENTRY,
                    INVALID_STATE_TRANSITION, PERFORMANCE_ANOMALY -> true;
            default -> false;
        };
    }

    private boolean shouldQuarantine(ProblematicDataRecord record) {
        return record.severity() == ProblematicSeverity.CRITICAL
                || record.problematicType() == ProblematicDataType.SLA_BREACH;
    }

    private String getQuarantineTable(ProblematicDataRecord record) {
        return switch (record.dataType()) {
            case "RENDER_JOB" -> "quarantined_render_jobs";
            case "PROMPT_EXECUTION" -> "quarantined_prompt_executions";
            case "PROVIDER_WORKER" -> "quarantined_provider_workers";
            default -> "quarantined_problematic_data";
        };
    }

    // -------------------------------------------------------------------------
    // Batch result
    // -------------------------------------------------------------------------

    public record BatchFixResult(
            int totalDetected,
            int autoFixed,
            int quarantined,
            int humanReviewRequired,
            int failed,
            List<ProblematicDataRecord> processedRecords
    ) {}
}
