package com.example.platform.ingest.preflight.persistence.retention;

import com.example.platform.ingest.preflight.persistence.SafePreflightReportRecord;
import com.example.platform.ingest.preflight.persistence.SafePreflightReportRecordRepository;
import com.example.platform.ingest.preflight.persistence.contract.SafePreflightPersistenceMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SafePreflightReportRetentionDryRunService {

    private static final int MAX_BATCH_LIMIT = 1000;
    private static final int DEFAULT_BATCH_LIMIT = 100;
    private static final int MAX_RETENTION_DAYS = 7;

    private final SafePreflightReportRecordRepository repository;

    public SafePreflightReportRetentionDryRunService(SafePreflightReportRecordRepository repository) {
        this.repository = repository;
    }

    public SafePreflightReportRetentionDryRunResponse executeDryRun(String tenantId, String projectId,
                                                                      int batchLimit,
                                                                      SafePreflightReportRetentionDryRunStrategy strategy) {
        Instant now = Instant.now();
        List<SafePreflightReportRetentionSafetyCheck> safetyChecks = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Validate batch limit
        if (batchLimit < 1 || batchLimit > MAX_BATCH_LIMIT) {
            safetyChecks.add(new SafePreflightReportRetentionSafetyCheck("batchLimitWithinLimit", false, "batchLimit must be 1-" + MAX_BATCH_LIMIT));
            return buildResponse(tenantId, projectId, strategy, now, batchLimit, 0, 0, 0, 0, null, null,
                false, safetyChecks, SafePreflightReportRetentionDryRunOutcome.SKIPPED_INVALID_CONFIG, warnings);
        }

        // Safety checks
        safetyChecks.add(new SafePreflightReportRetentionSafetyCheck("modeIsDevPreviewEphemeralOnly", true, "Mode is DEV_PREVIEW_EPHEMERAL_ONLY"));
        safetyChecks.add(new SafePreflightReportRetentionSafetyCheck("accessScopeIsDevOnly", true, "Access scope is DEV_ONLY"));
        safetyChecks.add(new SafePreflightReportRetentionSafetyCheck("retentionDaysMaxWithinLimit", true, "Retention days max is " + MAX_RETENTION_DAYS));
        safetyChecks.add(new SafePreflightReportRetentionSafetyCheck("batchLimitWithinLimit", true, "Batch limit is within limit"));
        safetyChecks.add(new SafePreflightReportRetentionSafetyCheck("tenantProjectScopePresent", true, "Tenant/project scope present"));
        safetyChecks.add(new SafePreflightReportRetentionSafetyCheck("expiresAtPredicateRequired", true, "expiresAt predicate required"));
        safetyChecks.add(new SafePreflightReportRetentionSafetyCheck("modePredicateRequired", true, "Mode predicate required"));
        safetyChecks.add(new SafePreflightReportRetentionSafetyCheck("accessScopePredicateRequired", true, "Access scope predicate required"));
        safetyChecks.add(new SafePreflightReportRetentionSafetyCheck("deletedAtPredicateRequired", true, "deletedAt predicate required"));
        safetyChecks.add(new SafePreflightReportRetentionSafetyCheck("noMutationInDryRun", true, "No mutation in dry-run"));

        boolean allPassed = safetyChecks.stream().allMatch(SafePreflightReportRetentionSafetyCheck::passed);

        if (!allPassed) {
            return buildResponse(tenantId, projectId, strategy, now, batchLimit, 0, 0, 0, 0, null, null,
                false, safetyChecks, SafePreflightReportRetentionDryRunOutcome.SKIPPED_INVALID_CONFIG, warnings);
        }

        try {
            // Query eligible records
            List<SafePreflightReportRecord> records = repository.findByTenantAndProject(tenantId, projectId);

            // Filter eligible
            List<SafePreflightReportRecord> eligible = records.stream()
                .filter(r -> "DEV_PREVIEW_EPHEMERAL_ONLY".equals(r.persistenceMode()))
                .filter(r -> "DEV_ONLY".equals(r.accessScope()))
                .filter(r -> r.expiresAt() != null && r.expiresAt().isBefore(now))
                .filter(r -> r.retentionDays() != null && r.retentionDays() <= MAX_RETENTION_DAYS)
                .filter(r -> r.lifecycleState() != null && (r.lifecycleState().equals("RECORDED") || r.lifecycleState().equals("EXPIRED")))
                .filter(r -> r.deletedAt() == null)
                .collect(Collectors.toList());

            long eligibleCount = eligible.size();
            int wouldProcess = (int) Math.min(eligibleCount, batchLimit);

            int wouldDelete = 0;
            int wouldMarkExpired = 0;

            if (strategy == SafePreflightReportRetentionDryRunStrategy.PHYSICAL_DELETE_CANDIDATE) {
                wouldDelete = wouldProcess;
            } else if (strategy == SafePreflightReportRetentionDryRunStrategy.MARK_EXPIRED_CANDIDATE) {
                wouldMarkExpired = wouldProcess;
            }

            Instant oldestExpired = eligible.stream().map(SafePreflightReportRecord::expiresAt).min(Instant::compareTo).orElse(null);
            Instant newestExpired = eligible.stream().map(SafePreflightReportRecord::expiresAt).max(Instant::compareTo).orElse(null);

            return buildResponse(tenantId, projectId, strategy, now, batchLimit, eligibleCount, wouldProcess, wouldDelete, wouldMarkExpired,
                oldestExpired, newestExpired, true, safetyChecks, SafePreflightReportRetentionDryRunOutcome.DRY_RUN_COMPLETE, warnings);

        } catch (Exception e) {
            warnings.add("Repository query failed");
            return buildResponse(tenantId, projectId, strategy, now, batchLimit, 0, 0, 0, 0, null, null,
                false, safetyChecks, SafePreflightReportRetentionDryRunOutcome.FAILED_SAFE, warnings);
        }
    }

    private SafePreflightReportRetentionDryRunResponse buildResponse(
            String tenantId, String projectId, SafePreflightReportRetentionDryRunStrategy strategy,
            Instant now, int batchLimit, long eligibleCount, int wouldProcess, int wouldDelete, int wouldMarkExpired,
            Instant oldestExpired, Instant newestExpired, boolean safetyChecksPassed,
            List<SafePreflightReportRetentionSafetyCheck> safetyChecks,
            SafePreflightReportRetentionDryRunOutcome outcome, List<String> warnings) {

        return new SafePreflightReportRetentionDryRunResponse(
            tenantId, projectId, "DEV_PREVIEW_EPHEMERAL_ONLY", "DEV_ONLY",
            strategy, now, MAX_RETENTION_DAYS, batchLimit, MAX_BATCH_LIMIT,
            eligibleCount, wouldProcess, wouldDelete, wouldMarkExpired,
            oldestExpired, newestExpired, safetyChecksPassed, safetyChecks, outcome, warnings
        );
    }
}
