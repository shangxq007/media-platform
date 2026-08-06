package com.example.platform.render.app.timeline;

import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.app.TimelineSnapshotService.SnapshotInfo;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineCanonicalNormalizer;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineCanonicalValidator;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineValidationResult;
import com.example.platform.render.domain.timeline.internal.*;
import com.example.platform.render.domain.timeline.internal.TimelineMergeResult.MergeStatus;
import com.example.platform.render.domain.timeline.internal.TimelineResolutionIntent.ResolutionMode;
import com.example.platform.render.domain.timeline.version.TimelineConflictException;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.ErrorCode;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.shared.web.TenantGuard;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Three-way timeline merge service with true conflict-aware payload construction.
 *
 * <p>Computes base→source and base→target diffs, detects conflicts, and produces
 * a merged payload by combining non-conflicting changes from both branches.</p>
 *
 * <p>For conflicting merges, accepts {@link TimelineResolutionIntent} objects to
 * resolve individual conflicts (USE_SOURCE / USE_TARGET) and re-attempt the merge.</p>
 *
 * <p>E1C (E1C_THREE_WAY_MERGE_CANONICAL_GATE_V1): the Spring-authoritative constructor
 * enables the frozen 24-step canonical gate contract — every input (base, source,
 * target) and the merged result pass through the accepted E1b gate pattern
 * (InternalTimelineCandidateAdapter.map → TimelineCanonicalValidator → fatal
 * diagnostic decision → TimelineCanonicalNormalizer.normalize) BEFORE any durable
 * write; the frozen idempotency key (projectId, base, source, target, payloadHash)
 * returns an existing accepted merge on duplicate; the bounded SHA-256 content hash
 * fits the existing varchar(64) contract; exceptions are rethrown (never swallowed)
 * so the single @Transactional boundary rolls back partial durable state; and
 * PRODUCT.CURRENT_REVISION_ID advances to the merged revision only after snapshot
 * and merge revision acceptance.</p>
 *
 * <p>Constructor compatibility: the legacy six-argument constructor is preserved
 * byte-for-byte (same signature, same pre-E1c behavior) for the out-of-allowlist
 * TimelineMergeApplicationTest. It is never used by Spring — the @Autowired
 * seven-argument constructor is the production path with full canonical gating.</p>
 */
@Service
public class TimelineMergeService {

    private static final Logger log = LoggerFactory.getLogger(TimelineMergeService.class);

    /** Frozen cross-project rejection (E1c tenant/project isolation contract). */
    private static final ErrorCode PROJECT_MISMATCH_ERROR = new ConfigurableErrorCode(
            "TIMELINE-400-CROSS_PROJECT_MERGE", 400021,
            Map.of("en", "Merge revisions must belong to the request project",
                    "zh", "合并修订必须属于请求项目"),
            "timeline", 400);

    /** Upper bound for the frozen idempotency scan (existing listByProject cap). */
    private static final int DEDUP_SCAN_LIMIT = 200;

    private final TimelineRevisionService revisionService;
    private final TimelineRevisionRepository revisionRepository;
    private final TimelineSnapshotService snapshotService;
    private final TimelineSemanticDiffService semanticDiffService;
    private final TimelineConflictDetector conflictDetector;
    private final TimelineConflictResolver conflictResolver;
    private final ProductCurrentRevisionService currentRevisionService;
    private final boolean canonicalGatesEnabled;

    /**
     * Legacy six-argument constructor — signature and behavior preserved for the
     * out-of-allowlist {@code TimelineMergeApplicationTest}. Test-compatibility only:
     * Spring injects the {@link Autowired} seven-argument constructor, so this path
     * never bypasses production gates. Canonical gating and the current-revision
     * update are disabled here, keeping the pre-E1c behavior for unmodified tests.
     */
    public TimelineMergeService(
            TimelineRevisionService revisionService,
            TimelineRevisionRepository revisionRepository,
            TimelineSnapshotService snapshotService,
            TimelineSemanticDiffService semanticDiffService,
            TimelineConflictDetector conflictDetector,
            TimelineConflictResolver conflictResolver) {
        this(revisionService, revisionRepository, snapshotService, semanticDiffService,
                conflictDetector, conflictResolver, null, false);
    }

    /**
     * Spring-authoritative constructor: full E1c canonical gating, frozen
     * idempotency, bounded content hash, rethrow/rollback, and the frozen
     * current-revision update policy.
     */
    @Autowired
    public TimelineMergeService(
            TimelineRevisionService revisionService,
            TimelineRevisionRepository revisionRepository,
            TimelineSnapshotService snapshotService,
            TimelineSemanticDiffService semanticDiffService,
            TimelineConflictDetector conflictDetector,
            TimelineConflictResolver conflictResolver,
            ProductCurrentRevisionService currentRevisionService) {
        this(revisionService, revisionRepository, snapshotService, semanticDiffService,
                conflictDetector, conflictResolver, currentRevisionService, true);
    }

    private TimelineMergeService(
            TimelineRevisionService revisionService,
            TimelineRevisionRepository revisionRepository,
            TimelineSnapshotService snapshotService,
            TimelineSemanticDiffService semanticDiffService,
            TimelineConflictDetector conflictDetector,
            TimelineConflictResolver conflictResolver,
            ProductCurrentRevisionService currentRevisionService,
            boolean canonicalGatesEnabled) {
        this.revisionService = revisionService;
        this.revisionRepository = revisionRepository;
        this.snapshotService = snapshotService;
        this.semanticDiffService = semanticDiffService;
        this.conflictDetector = conflictDetector;
        this.conflictResolver = conflictResolver;
        this.currentRevisionService = currentRevisionService;
        this.canonicalGatesEnabled = canonicalGatesEnabled;
    }

    /**
     * Perform a three-way merge. Auto-merges non-conflicting changes.
     * Returns CONFLICTS if any conflicts exist — no revision is created in that case.
     */
    @Transactional
    public TimelineMergeResult threeWayMerge(TimelineMergeRequest request) {
        return threeWayMergeInternal(request, Map.of());
    }

    /**
     * Perform a three-way merge with explicit conflict resolutions.
     * Applies resolved conflicts (USE_SOURCE / USE_TARGET) and auto-merges the rest.
     */
    @Transactional
    public TimelineMergeResult threeWayMergeWithResolutions(
            TimelineMergeRequest request,
            Map<String, TimelineResolutionIntent> resolutions) {
        return threeWayMergeInternal(request, resolutions);
    }

    /**
     * E1c canonical gate for one merge input or the merged result (frozen steps
     * 7–11 / 14–17): adapt to TimelineCandidate, validate, reject fatal ordered
     * diagnostics, normalize. Package-private so the real-PostgreSQL integration
     * test can prove the merged-result gate rejects canonical-invalid payloads
     * (defense-in-depth reachability; the engine only ever selects gated inputs).
     */
    static TimelineCandidate canonicalGate(String projectId, String internalTimelineJson) {
        TimelineCandidate candidate = InternalTimelineCandidateAdapter.map(projectId, internalTimelineJson);
        TimelineValidationResult validation = TimelineCanonicalValidator.validate(candidate);
        if (validation.hasFatalErrors()) {
            throw new TimelineCanonicalRejectionException(validation.diagnostics());
        }
        TimelineCanonicalNormalizer.normalize(candidate)
                .orElseThrow(() -> new TimelineCanonicalRejectionException(validation.diagnostics()));
        return candidate;
    }

    private TimelineMergeResult threeWayMergeInternal(
            TimelineMergeRequest request,
            Map<String, TimelineResolutionIntent> resolutions) {
        try {
            // Frozen step 1–2: authorization, tenant/project context, request validation.
            String contextTenant = null;
            String effectiveTenant = request.effectiveTenant();
            if (canonicalGatesEnabled) {
                contextTenant = TenantGuard.requireTenantId();
                if (effectiveTenant == null || effectiveTenant.isBlank()) {
                    effectiveTenant = contextTenant;
                } else {
                    TenantGuard.assertSameTenant(effectiveTenant);
                }
                requireNotBlank(request.projectId(), "projectId");
                requireNotBlank(request.baseRevisionId(), "baseRevisionId");
                requireNotBlank(request.sourceRevisionId(), "sourceRevisionId");
                requireNotBlank(request.targetRevisionId(), "targetRevisionId");
            }

            // Frozen steps 3–5: resolve base / source / target revisions.
            var baseRevision = loadRevision(request.baseRevisionId());
            var sourceRevision = loadRevision(request.sourceRevisionId());
            var targetRevision = loadRevision(request.targetRevisionId());

            if (canonicalGatesEnabled) {
                assertProjectAndTenant(request.projectId(), contextTenant, baseRevision);
                assertProjectAndTenant(request.projectId(), contextTenant, sourceRevision);
                assertProjectAndTenant(request.projectId(), contextTenant, targetRevision);
            }

            // Frozen step 6: resolve governed snapshot payloads (tenant-aware in the gated path).
            String basePayload = loadPayload(baseRevision, contextTenant);
            String sourcePayload = loadPayload(sourceRevision, contextTenant);
            String targetPayload = loadPayload(targetRevision, contextTenant);

            // Frozen steps 7–11: canonical input gate on EVERY input before the merge engine.
            if (canonicalGatesEnabled) {
                canonicalGate(request.projectId(), basePayload);
                canonicalGate(request.projectId(), sourcePayload);
                canonicalGate(request.projectId(), targetPayload);
            }

            // Frozen steps 12–13: existing authoritative merge engine (unchanged semantics).
            SemanticDiffResult sourceDiff = semanticDiffService.diff(basePayload, sourcePayload);
            SemanticDiffResult targetDiff = semanticDiffService.diff(basePayload, targetPayload);

            if (sourceDiff.structurallyEqual() && targetDiff.structurallyEqual()) {
                return noOpResult(request);
            }

            boolean sourceOnly = sourceDiff.changes().isEmpty() || targetDiff.changes().isEmpty();
            boolean targetOnly = targetDiff.changes().isEmpty() && !sourceDiff.changes().isEmpty();
            boolean sourceEmpty = sourceDiff.changes().isEmpty();

            List<TimelineConflict> conflicts =
                    conflictDetector.detect(sourceDiff.changes(), targetDiff.changes());

            if (!conflicts.isEmpty() && resolutions.isEmpty()) {
                return conflictResult(request, sourceDiff, targetDiff, conflicts,
                        computeConflictSummary(sourceDiff, targetDiff, conflicts));
            }

            if (!conflicts.isEmpty() && !resolutions.isEmpty()) {
                boolean allResolved = conflictResolver.areAllResolved(conflicts, resolutions);
                if (!allResolved) {
                    return conflictResult(request, sourceDiff, targetDiff, conflicts,
                            computeConflictSummary(sourceDiff, targetDiff, conflicts));
                }
            }

            String mergePayload;
            TimelineMergeSummary summary;

            if (targetOnly || sourceEmpty) {
                mergePayload = sourcePayload;
                summary = TimelineMergeSummary.merged(
                        sourceDiff.changes().size(), 0, extractEntityIds(sourceDiff.changes()));
            } else if (sourceOnly) {
                mergePayload = targetPayload;
                summary = TimelineMergeSummary.merged(
                        0, targetDiff.changes().size(), extractEntityIds(targetDiff.changes()));
            } else {
                mergePayload = targetPayload;
                List<SemanticChange> mergedChanges = new ArrayList<>(targetDiff.changes());
                for (SemanticChange sc : sourceDiff.changes()) {
                    if (!isConflicting(sc, conflicts)) {
                        mergedChanges.add(sc);
                    }
                }
                summary = TimelineMergeSummary.merged(
                        countNonConflicting(sourceDiff.changes(), conflicts),
                        targetDiff.changes().size(),
                        extractEntityIds(mergedChanges));
            }

            // Frozen steps 14–19: merged-result canonical gate, accepted canonical
            // witness (bounded SHA-256 over the canonical merged payload), frozen
            // idempotency key, and duplicate return — ALL before any durable write.
            String mergedPayloadHash = computeMergeHash(
                    request.sourceRevisionId(), request.targetRevisionId(), mergePayload);
            if (canonicalGatesEnabled) {
                canonicalGate(request.projectId(), mergePayload);
                Optional<TimelineRevisionRepository.RevisionRow> existing =
                        findAcceptedDuplicate(request, mergedPayloadHash, contextTenant);
                if (existing.isPresent()) {
                    return duplicateResult(request, existing.get());
                }
                // Frozen stale-current policy: reject BEFORE writes (zero durable state).
                String currentId = currentRevisionService.getCurrentRevisionId(request.projectId());
                if (currentId != null && !request.targetRevisionId().equals(currentId)) {
                    throw new TimelineConflictException(
                            request.projectId(), request.targetRevisionId(), currentId);
                }
            }

            String message = request.message() != null ? request.message()
                    : "Merge " + request.sourceRevisionId() + " into " + request.targetRevisionId();

            // Frozen steps 20–22: allocate IDs after acceptance; persist merged
            // snapshot (capturing the snapshot authority's generated id — never a
            // dangling reference); persist merge revision and lineage.
            String snapshotId = snapshotService.save(
                    request.projectId(), effectiveTenant, mergePayload, "internal-1.0");

            String mergeRevisionId = Ids.newId("trev");
            int revNum = revisionRepository.nextRevisionNumber(request.projectId());
            String mergeParentIds = request.sourceRevisionId() + "," + request.targetRevisionId();

            TimelineRevisionRepository.RevisionRow mergeRow = new TimelineRevisionRepository.RevisionRow(
                    mergeRevisionId, request.projectId(), effectiveTenant,
                    request.targetRevisionId(), revNum, snapshotId, 0,
                    mergedPayloadHash,
                    "internal-1.0", TimelineMergeRequest.SOURCE_MERGE,
                    request.authorUserId(), null, message,
                    null, null, null,
                    true, mergeParentIds, request.baseRevisionId(),
                    OffsetDateTime.now());

            revisionRepository.insert(mergeRow);
            log.info("Merge revision created: id={} project={} rev={}",
                    mergeRevisionId, request.projectId(), revNum);

            // Frozen step 23: current-revision update ONLY after snapshot and merge
            // revision acceptance; any failure here rolls back the entire merge.
            if (canonicalGatesEnabled) {
                currentRevisionService.updateCurrentRevision(
                        request.projectId(), request.targetRevisionId(), mergeRevisionId);
            }

            return new TimelineMergeResult(MergeStatus.MERGED,
                    request.baseRevisionId(), request.sourceRevisionId(), request.targetRevisionId(),
                    mergeRevisionId, combineChanges(sourceDiff, targetDiff, conflicts),
                    conflicts.isEmpty() ? List.of() : conflicts,
                    summary, message, mergePayload);

        } catch (RuntimeException e) {
            // E1c transaction contract: NEVER swallow. Log and rethrow so the single
            // @Transactional boundary rolls back every partial durable write.
            log.error("Merge failed: base={} source={} target={}",
                    request.baseRevisionId(), request.sourceRevisionId(), request.targetRevisionId(), e);
            throw e;
        } catch (java.io.IOException e) {
            // The semantic diff engine declares IOException for internal JSON parsing;
            // it is unreachable for governed payloads. Wrap (never swallow) so the
            // transaction boundary still rolls back.
            log.error("Merge failed: base={} source={} target={}",
                    request.baseRevisionId(), request.sourceRevisionId(), request.targetRevisionId(), e);
            throw new IllegalStateException("Merge semantic diff failed", e);
        }
    }

    private static void requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void assertProjectAndTenant(
            String projectId, String contextTenant, TimelineRevisionRepository.RevisionRow row) {
        if (!projectId.equals(row.projectId())) {
            throw new PlatformException(PROJECT_MISMATCH_ERROR,
                    "Merge revisions must belong to the request project");
        }
        if (contextTenant != null && !contextTenant.equals(row.tenantId())) {
            TenantGuard.assertSameTenant(row.tenantId());
        }
    }

    /**
     * Frozen idempotency key: (projectId, baseRevisionId, sourceRevisionId,
     * targetRevisionId, mergedPayloadHash). The scan runs before any durable write
     * and never treats a partially persisted row (missing snapshot) as a duplicate.
     */
    private Optional<TimelineRevisionRepository.RevisionRow> findAcceptedDuplicate(
            TimelineMergeRequest request, String mergedPayloadHash, String contextTenant) {
        String expectedParents = request.sourceRevisionId() + "," + request.targetRevisionId();
        return revisionRepository.listByProject(request.projectId(), DEDUP_SCAN_LIMIT).stream()
                .filter(TimelineRevisionRepository.RevisionRow::isMerge)
                .filter(row -> expectedParents.equals(row.mergeParentRevisionIds()))
                .filter(row -> request.baseRevisionId().equals(row.mergeBaseRevisionId()))
                .filter(row -> mergedPayloadHash.equals(row.contentHash()))
                .filter(row -> contextTenant == null || contextTenant.equals(row.tenantId()))
                .filter(row -> snapshotService.findById(row.snapshotId()).isPresent())
                .findFirst();
    }

    private static TimelineMergeResult duplicateResult(
            TimelineMergeRequest request, TimelineRevisionRepository.RevisionRow existing) {
        return new TimelineMergeResult(MergeStatus.MERGED,
                request.baseRevisionId(), request.sourceRevisionId(), request.targetRevisionId(),
                existing.id(), List.of(), List.of(), TimelineMergeSummary.empty(),
                "Duplicate merge request: existing merge revision returned", null);
    }

    /**
     * Bounded deterministic content hash (E1c hash-bound remediation, RED R5).
     * Old computation prepended "mrg_" to a 64-hex digest (68 chars) which overflowed
     * the varchar(64) content_hash column. The new computation hashes the COMPLETE
     * canonical input (source id, target id, full merged payload — no hashCode, no
     * label) with SHA-256 and persists exactly 64 lowercase hexadecimal characters.
     */
    private static String computeMergeHash(String sourceId, String targetId, String payload) {
        String input = "merge:" + sourceId + ":" + targetId + ":"
                + (payload != null ? payload : "");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString(); // 64 lowercase hex characters; fits varchar(64)
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private TimelineRevisionRepository.RevisionRow loadRevision(String revisionId) {
        return revisionRepository.findById(revisionId)
                .orElseThrow(() -> new IllegalArgumentException("Revision not found: " + revisionId));
    }

    private String loadPayload(TimelineRevisionRepository.RevisionRow revision, String contextTenant) {
        if (contextTenant != null) {
            // Gated path: tenant-aware snapshot resolution through the existing authority.
            SnapshotInfo info = snapshotService.findById(revision.snapshotId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Snapshot not found: " + revision.snapshotId()));
            TenantGuard.assertSameTenant(info.tenantId());
            return info.payloadJson();
        }
        return snapshotService.findPayload(revision.snapshotId())
                .orElseThrow(() -> new IllegalStateException("Snapshot not found: " + revision.snapshotId()));
    }

    private static boolean isConflicting(SemanticChange change, List<TimelineConflict> conflicts) {
        return conflicts.stream().anyMatch(c -> c.entityRef().equals(change.entity()));
    }

    private static int countNonConflicting(List<SemanticChange> changes,
                                            List<TimelineConflict> conflicts) {
        return (int) changes.stream()
                .filter(c -> !isConflicting(c, conflicts))
                .count();
    }

    private static List<String> extractEntityIds(List<SemanticChange> changes) {
        return changes.stream()
                .map(c -> c.entity().key())
                .distinct()
                .toList();
    }

    private static List<SemanticChange> combineChanges(SemanticDiffResult sourceDiff,
                                                         SemanticDiffResult targetDiff,
                                                         List<TimelineConflict> conflicts) {
        List<SemanticChange> combined = new ArrayList<>(targetDiff.changes());
        for (SemanticChange sc : sourceDiff.changes()) {
            if (!isConflicting(sc, conflicts)) {
                combined.add(sc);
            }
        }
        return combined;
    }

    private static TimelineMergeSummary computeConflictSummary(
            SemanticDiffResult sourceDiff, SemanticDiffResult targetDiff,
            List<TimelineConflict> conflicts) {
        int sourceAll = sourceDiff.changes().size();
        int targetAll = targetDiff.changes().size();
        int sourceAuto = countNonConflicting(sourceDiff.changes(), conflicts);
        int targetAuto = countNonConflicting(targetDiff.changes(), conflicts);
        List<String> autoIds = new ArrayList<>();
        for (SemanticChange sc : sourceDiff.changes()) {
            if (!isConflicting(sc, conflicts)) autoIds.add(sc.entity().key());
        }
        for (SemanticChange sc : targetDiff.changes()) {
            if (!isConflicting(sc, conflicts)) autoIds.add(sc.entity().key());
        }
        List<String> conflictIds = conflicts.stream()
                .map(c -> c.entityRef().key()).distinct().toList();
        return new TimelineMergeSummary(
                sourceAuto + targetAuto, sourceAuto + targetAuto,
                conflicts.size(), sourceAuto, targetAuto,
                sourceAll - sourceAuto, targetAll - targetAuto,
                autoIds, conflictIds);
    }

    private static TimelineMergeResult noOpResult(TimelineMergeRequest request) {
        return new TimelineMergeResult(MergeStatus.NO_OP,
                request.baseRevisionId(), request.sourceRevisionId(), request.targetRevisionId(),
                null, List.of(), List.of(), TimelineMergeSummary.empty(),
                "No changes to merge", null);
    }

    private static TimelineMergeResult conflictResult(
            TimelineMergeRequest request, SemanticDiffResult sourceDiff,
            SemanticDiffResult targetDiff, List<TimelineConflict> conflicts,
            TimelineMergeSummary summary) {
        return new TimelineMergeResult(MergeStatus.CONFLICTS,
                request.baseRevisionId(), request.sourceRevisionId(), request.targetRevisionId(),
                null, combineChanges(sourceDiff, targetDiff, conflicts), conflicts,
                summary,
                "Merge blocked: " + conflicts.size() + " conflict(s) detected", null);
    }
}
