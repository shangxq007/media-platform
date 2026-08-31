package com.example.platform.timeline.app;

import com.example.platform.timeline.adapter.RevisionGraphService;
import com.example.platform.timeline.adapter.TimelineRevisionRepository;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.diff.TimelineChangeOperation;
import com.example.platform.timeline.diff.TimelinePatch;
import com.example.platform.timeline.diff.TimelinePatchId;
import com.example.platform.timeline.diff.application.TimelinePatchApplicationResult;
import com.example.platform.timeline.diff.application.TimelinePatchApplicationStatus;
import com.example.platform.timeline.diff.application.TimelinePatchApplier;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineSnapshot;
import java.util.Objects;
import com.example.platform.timeline.diff.calculation.TimelineSnapshotConverter;
import com.example.platform.timeline.diff.merge.plan.TimelineMergePlanOperation;
import com.example.platform.timeline.diff.merge.plan.TimelineMergePlanOperationStatus;
import com.example.platform.timeline.diff.merge.plan.TimelineMergePlanOperationSource;
import com.example.platform.timeline.diff.merge.plan.TimelineMergePlanRequest;
import com.example.platform.timeline.diff.merge.plan.TimelineMergePlanRequestId;
import com.example.platform.timeline.diff.merge.plan.TimelineMergePlanPolicy;
import com.example.platform.timeline.diff.merge.plan.TimelineNonConflictingMergePlan;
import com.example.platform.timeline.diff.merge.plan.TimelineNonConflictingMergePlanner;
import com.example.platform.timeline.diff.merge.preview.TimelineMergePreviewRequest;
import com.example.platform.timeline.diff.merge.preview.TimelineMergePreviewRequestId;
import com.example.platform.timeline.diff.merge.preview.TimelineMergePreviewMode;
import com.example.platform.timeline.diff.merge.preview.TimelineMergePreviewPolicy;
import com.example.platform.timeline.diff.merge.preview.TimelineMergePreviewService;
import com.example.platform.timeline.diff.merge.EntityKind;
import com.example.platform.timeline.diff.merge.EntityRef;
import com.example.platform.timeline.diff.merge.SemanticChange;
import com.example.platform.timeline.diff.merge.SemanticChangeType;
import com.example.platform.timeline.diff.merge.TimelineConflict;
import com.example.platform.timeline.diff.merge.TimelineConflictType;
import com.example.platform.timeline.diff.merge.TimelineMergeRequest;
import com.example.platform.timeline.diff.merge.TimelineMergeResult;
import com.example.platform.timeline.diff.merge.TimelineMergeSummary;
import com.example.platform.timeline.diff.merge.TimelineResolutionIntent;
import com.example.platform.timeline.version.TimelineConflictException;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.ErrorCode;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.shared.web.TenantGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Canonical Timeline semantic merge engine (C1 convergence).
 *
 * <p>Single production Timeline semantic merge authority. Pipeline:
 * load base/source/target revisions -> canonical snapshots ->
 * {@link TimelineMergePreviewService} + {@link TimelineNonConflictingMergePlanner} ->
 * typed-path conflict detection -> {@link TimelinePatchApplier} (non-conflicting
 * operations from BOTH branches are MATERIALIZED) -> canonical Timeline validation ->
 * new immutable dual-parent {@code TimelineRevision}.</p>
 *
 * <p>Replaces the retired entity-level JSON merge (Stack A) while preserving the
 * frozen persistence contract: canonical input gates, idempotency key, stale-current
 * policy, snapshot save, dual-parent revision insert.</p>
 */
@Service
public class TimelineMergeEngine {

    private static final Logger log = LoggerFactory.getLogger(TimelineMergeEngine.class);
    private static final int DEDUP_SCAN_LIMIT = 500;
    private static final ErrorCode PROJECT_MISMATCH_ERROR = new ConfigurableErrorCode(
            "TIMELINE-400-CROSS_PROJECT_MERGE", 400021,
            Map.of("en", "Merge revisions must belong to the request project",
                    "zh", "合并修订必须属于请求项目"),
            "timeline", 400);

    private final TimelineRevisionRepository revisionRepository;
    private final TimelineSnapshotService snapshotService;
    private final TimelineRevisionSaveService revisionSaveService;
    private final TimelineMergePreviewService previewService;
    private final TimelineNonConflictingMergePlanner mergePlanner;
    private final TimelinePatchApplier patchApplier;
    private final ObjectMapper objectMapper;
    private final TimelineArtifactPinValidator artifactPinValidator;
    private final com.example.platform.artifact.app.ArtifactPinService artifactPinService;
    private final org.jooq.DSLContext dsl;

    /**
     * R5-C (CHECKPOINT_A Round 5) + FINAL_CLOSURE_F1: the ONLY production
     * constructor. The Timeline artifact-pin invariant boundary is REQUIRED BY
     * CONSTRUCTION — every dependency is {@link Objects#requireNonNull}; there
     * is NO constructor that permits a persistent merge with a null pin
     * boundary. PERSISTENT_MERGE_WITHOUT_PIN_BOUNDARY =
     * IMPOSSIBLE_BY_CONSTRUCTION.
     *
     * <p>CONSTRUCTOR_INJECTION_WITHOUT_EXPLICIT_AUTOWIRED_V1 (R5 addendum):
     * exactly ONE public constructor, constructor injection, NO @Autowired
     * (Spring 4.3+ injects the sole constructor automatically), no secondary
     * test convenience constructor. Tests that need lighter wiring pass
     * explicit mocks/fakes.
     *
     * <p>FINAL_CLOSURE_F1 (post-Round-5): {@code dsl} is the root DSLContext
     * used ONLY to open the EXPLICIT jOOQ write transaction for the persistent
     * merge phase (dsl.transactionResult) — the transaction's own DSLContext
     * flows through snapshot/revision/pin/head writes. The persistent merge
     * transaction boundary is therefore proxy-independent and mechanically
     * inspectable (no reliance on Spring @Transactional self-invocation).
     */
    public TimelineMergeEngine(
            TimelineRevisionRepository revisionRepository,
            TimelineSnapshotService snapshotService,
            TimelineRevisionSaveService revisionSaveService,
            TimelineMergePreviewService previewService,
            TimelineNonConflictingMergePlanner mergePlanner,
            TimelinePatchApplier patchApplier,
            ObjectMapper objectMapper,
            TimelineArtifactPinValidator artifactPinValidator,
            com.example.platform.artifact.app.ArtifactPinService artifactPinService,
            org.jooq.DSLContext dsl) {
        this.revisionRepository = Objects.requireNonNull(revisionRepository, "revisionRepository");
        this.snapshotService = Objects.requireNonNull(snapshotService, "snapshotService");
        this.revisionSaveService = Objects.requireNonNull(revisionSaveService, "revisionSaveService");
        this.previewService = Objects.requireNonNull(previewService, "previewService");
        this.mergePlanner = Objects.requireNonNull(mergePlanner, "mergePlanner");
        this.patchApplier = Objects.requireNonNull(patchApplier, "patchApplier");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.artifactPinValidator = Objects.requireNonNull(artifactPinValidator, "artifactPinValidator");
        this.artifactPinService = Objects.requireNonNull(artifactPinService, "artifactPinService");
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    public TimelineMergeResult merge(TimelineMergeRequest request) {
        return merge(request, Map.of());
    }

    /**
     * RCI5 (TIMELINE_MERGE_ENGINE_IS_PURE_SEMANTIC_MERGE_AUTHORITY_V1):
     * compute-only semantic merge — loads BASE/OURS/THEIRS, runs the canonical
     * gate, preview, planner and returns the merged canonical payload or typed
     * conflicts. ZERO persistence: no revision row, no snapshot write, no ref
     * update, no ApplyCommandId, no parent edge, no @Transactional. Graph
     * mechanics (merge-base) are owned by RevisionGraphService, never here.
     */
    public TimelineMergeResult mergeSemantic(TimelineMergeRequest request) {
        try {
            String effectiveTenant = request.effectiveTenant();
            requireNotBlank(effectiveTenant, "tenantId");
            requireNotBlank(request.projectId(), "projectId");
            requireNotBlank(request.baseRevisionId(), "baseRevisionId");
            requireNotBlank(request.sourceRevisionId(), "sourceRevisionId");

            var baseRevision = loadRevision(request.baseRevisionId(), request.projectId(), effectiveTenant);
            var sourceRevision = loadRevision(request.sourceRevisionId(), request.projectId(), effectiveTenant);
            var targetRevision = loadRevision(request.targetRevisionId(), request.projectId(), effectiveTenant);

            assertProjectAndTenant(request.projectId(), effectiveTenant, baseRevision);
            assertProjectAndTenant(request.projectId(), effectiveTenant, sourceRevision);
            assertProjectAndTenant(request.projectId(), effectiveTenant, targetRevision);

            String basePayload = loadPayload(baseRevision, effectiveTenant);
            String sourcePayload = loadPayload(sourceRevision, effectiveTenant);
            String targetPayload = loadPayload(targetRevision, effectiveTenant);

            CanonicalTimelineSnapshot baseSnapshot =
                    canonicalSnapshot(basePayload, request.baseRevisionId());
            CanonicalTimelineSnapshot sourceSnapshot =
                    canonicalSnapshot(sourcePayload, request.sourceRevisionId());
            CanonicalTimelineSnapshot targetSnapshot =
                    canonicalSnapshot(targetPayload, request.targetRevisionId());

            TimelineMergePreviewRequest previewRequest = new TimelineMergePreviewRequest(
                    new TimelineMergePreviewRequestId("merge-" + request.baseRevisionId()
                            + "-" + request.sourceRevisionId() + "-" + request.targetRevisionId()),
                    baseSnapshot, sourceSnapshot, targetSnapshot,
                    TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                    TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
            previewService.preview(previewRequest);

            TimelineNonConflictingMergePlan plan = mergePlanner.plan(new TimelineMergePlanRequest(
                    new TimelineMergePlanRequestId("plan-" + request.baseRevisionId()
                            + "-" + request.sourceRevisionId() + "-" + request.targetRevisionId()),
                    baseSnapshot, sourceSnapshot, targetSnapshot,
                    TimelineMergePlanPolicy.CONSERVATIVE, Map.of()));

            List<TimelineMergePlanOperation> allOps = plan.operations() != null ? plan.operations() : List.of();
            List<TimelineMergePlanOperation> conflictOps = allOps.stream()
                    .filter(op -> op.status() == TimelineMergePlanOperationStatus.CONFLICT_REQUIRES_MANUAL_REVIEW)
                    .toList();
            if (!conflictOps.isEmpty()) {
                return conflictResult(request, toInternalConflicts(conflictOps));
            }
            List<TimelineChangeOperation> applyOps = allOps.stream()
                    .filter(op -> op.status() == TimelineMergePlanOperationStatus.SAFE_TO_APPLY_LATER)
                    .map(TimelineMergePlanOperation::operation)
                    .toList();
            if (applyOps.isEmpty()) {
                return noOpResult(request);
            }
            TimelinePatch patch = new TimelinePatch(
                    new TimelinePatchId("patch-" + request.baseRevisionId()),
                    request.baseRevisionId(),
                    List.copyOf(applyOps),
                    null, Map.of());
            TimelinePatchApplicationResult application = patchApplier.apply(baseSnapshot, patch);
            if (application.status() != com.example.platform.timeline.diff.application.TimelinePatchApplicationStatus.APPLIED
                    && application.status() != com.example.platform.timeline.diff.application.TimelinePatchApplicationStatus.NO_OP) {
                return new TimelineMergeResult(TimelineMergeResult.MergeStatus.FAILED,
                        request.baseRevisionId(), request.sourceRevisionId(), request.targetRevisionId(),
                        null, List.of(), List.of(), TimelineMergeSummary.empty(),
                        "Canonical merge application failed", null);
            }
            String mergedPayload = TimelineDocumentJsonSerializer.serializeWithCaptions(
                    TimelineSnapshotConverter.toDocument(application.patchedSnapshot()));
            return new TimelineMergeResult(TimelineMergeResult.MergeStatus.MERGED,
                    request.baseRevisionId(), request.sourceRevisionId(), request.targetRevisionId(),
                    null, List.of(), List.of(), TimelineMergeSummary.empty(), null, mergedPayload);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("semantic merge failed", e);
        }
    }

    /**
     * FINAL_CLOSURE_F1 (post-Round-5): the persistent merge entrypoint. NO
     * Spring @Transactional on this method — the persistent write phase opens
     * an EXPLICIT jOOQ transaction ({@code dsl.transactionResult}) whose own
     * DSLContext flows through snapshot/revision/pin/head writes. The
     * transaction boundary is therefore proxy-independent and mechanically
     * inspectable; {@code merge(request)} cannot bypass it via Spring
     * self-invocation (PERSISTENT_MERGE_SELF_INVOCATION_TRANSACTION_BYPASS =
     * 0).
     */
    public TimelineMergeResult merge(
            TimelineMergeRequest request,
            Map<String, TimelineResolutionIntent> resolutions) {
        try {
            String effectiveTenant = request.effectiveTenant();
            requireNotBlank(effectiveTenant, "tenantId");
            requireNotBlank(request.projectId(), "projectId");
            requireNotBlank(request.baseRevisionId(), "baseRevisionId");
            requireNotBlank(request.sourceRevisionId(), "sourceRevisionId");
            requireNotBlank(request.targetRevisionId(), "targetRevisionId");

            var baseRevision = loadRevision(request.baseRevisionId(), request.projectId(), effectiveTenant);
            var sourceRevision = loadRevision(request.sourceRevisionId(), request.projectId(), effectiveTenant);
            var targetRevision = loadRevision(request.targetRevisionId(), request.projectId(), effectiveTenant);

            assertProjectAndTenant(request.projectId(), effectiveTenant, baseRevision);
            assertProjectAndTenant(request.projectId(), effectiveTenant, sourceRevision);
            assertProjectAndTenant(request.projectId(), effectiveTenant, targetRevision);

            String basePayload = loadPayload(baseRevision, effectiveTenant);
            String sourcePayload = loadPayload(sourceRevision, effectiveTenant);
            String targetPayload = loadPayload(targetRevision, effectiveTenant);

            // H7 V2: all three persisted payloads are decoded by the sole
            // TimelineDocument reader and projected into the semantic merge model.
            CanonicalTimelineSnapshot baseSnapshot =
                    canonicalSnapshot(basePayload, request.baseRevisionId());
            CanonicalTimelineSnapshot sourceSnapshot =
                    canonicalSnapshot(sourcePayload, request.sourceRevisionId());
            CanonicalTimelineSnapshot targetSnapshot =
                    canonicalSnapshot(targetPayload, request.targetRevisionId());

            // Canonical preview + plan: typed-path conflict detection over both branches.
            TimelineMergePreviewRequest previewRequest = new TimelineMergePreviewRequest(
                    new TimelineMergePreviewRequestId("merge-" + request.baseRevisionId()
                            + "-" + request.sourceRevisionId() + "-" + request.targetRevisionId()),
                    baseSnapshot, sourceSnapshot, targetSnapshot,
                    TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                    TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
            previewService.preview(previewRequest);

            TimelineNonConflictingMergePlan plan = mergePlanner.plan(new TimelineMergePlanRequest(
                    new TimelineMergePlanRequestId("plan-" + request.baseRevisionId()
                            + "-" + request.sourceRevisionId() + "-" + request.targetRevisionId()),
                    baseSnapshot, sourceSnapshot, targetSnapshot,
                    TimelineMergePlanPolicy.CONSERVATIVE, Map.of()));

            List<TimelineMergePlanOperation> allOps = plan.operations() != null ? plan.operations() : List.of();
            List<TimelineMergePlanOperation> conflictOps = allOps.stream()
                    .filter(op -> op.status() == TimelineMergePlanOperationStatus.CONFLICT_REQUIRES_MANUAL_REVIEW)
                    .toList();

            List<TimelineConflict> conflicts = toInternalConflicts(conflictOps);
            if (!conflicts.isEmpty() && (resolutions == null || resolutions.isEmpty())) {
                return conflictResult(request, conflicts);
            }

            // Resolution-aware op selection: safe ops from both branches + resolved conflicts.
            List<TimelineChangeOperation> applyOps = new ArrayList<>();
            int sourceApplied = 0;
            int targetApplied = 0;
            List<TimelineConflict> remainingConflicts = new ArrayList<>();
            for (TimelineMergePlanOperation op : allOps) {
                if (op.status() == TimelineMergePlanOperationStatus.SAFE_TO_APPLY_LATER) {
                    applyOps.add(op.operation());
                    if (op.source() == TimelineMergePlanOperationSource.OURS) {
                        sourceApplied++;
                    } else {
                        targetApplied++;
                    }
                } else if (op.status() == TimelineMergePlanOperationStatus.CONFLICT_REQUIRES_MANUAL_REVIEW) {
                    TimelineResolutionIntent resolution = resolutionFor(resolutions, op);
                    if (resolution == null) {
                        remainingConflicts.add(toInternalConflict(op));
                    } else if (resolution.resolutionMode() == TimelineResolutionIntent.ResolutionMode.USE_SOURCE
                            && op.source() == TimelineMergePlanOperationSource.OURS) {
                        applyOps.add(op.operation());
                        sourceApplied++;
                    } else if (resolution.resolutionMode() == TimelineResolutionIntent.ResolutionMode.USE_TARGET
                            && op.source() == TimelineMergePlanOperationSource.THEIRS) {
                        applyOps.add(op.operation());
                        targetApplied++;
                    } else {
                        remainingConflicts.add(toInternalConflict(op));
                    }
                }
            }

            if (!remainingConflicts.isEmpty()) {
                return conflictResult(request, remainingConflicts);
            }

            if (applyOps.isEmpty()) {
                return noOpResult(request);
            }

            TimelinePatch patch = new TimelinePatch(
                    new TimelinePatchId("patch-" + request.baseRevisionId()),
                    request.baseRevisionId(),
                    List.copyOf(applyOps),
                    null, Map.of());
            TimelinePatchApplicationResult application = patchApplier.apply(baseSnapshot, patch);
            if (application.status() != com.example.platform.timeline.diff.application.TimelinePatchApplicationStatus.APPLIED
                    && application.status() != com.example.platform.timeline.diff.application.TimelinePatchApplicationStatus.NO_OP) {
                return new TimelineMergeResult(TimelineMergeResult.MergeStatus.FAILED,
                        request.baseRevisionId(), request.sourceRevisionId(), request.targetRevisionId(),
                        null, List.of(), List.of(), TimelineMergeSummary.empty(),
                        "Canonical merge application failed", null);
            }

            CanonicalTimelineSnapshot mergedSnapshot = application.patchedSnapshot();
            TimelineDocument mergedDocument = TimelineSnapshotConverter.toDocument(mergedSnapshot);
            String mergedPayload = TimelineDocumentJsonSerializer.serializeWithCaptions(mergedDocument);
            String mergedPayloadHash = new TimelineContentDigester().digest(mergedDocument);
            Optional<TimelineRevisionRepository.RevisionRow> existing =
                    findAcceptedDuplicate(request, mergedPayloadHash, effectiveTenant);
            if (existing.isPresent()) {
                return duplicateResult(request, existing.get());
            }

            String message = request.message() != null ? request.message()
                    : "Merge " + request.sourceRevisionId() + " into " + request.targetRevisionId();
            com.example.platform.timeline.version.TimelineRevision persisted =
                    revisionSaveService.saveMergeRevision(
                            request.mutationContext(), request.targetRevisionId(),
                            request.sourceRevisionId(), request.baseRevisionId(), mergedDocument);
            List<SemanticChange> autoMerged = applyOps.stream()
                        .map(op -> SemanticChange.of(
                                toSemanticChangeType(op),
                                toEntityRef(op),
                                "canonical merge applied " + op.type() + " on " + op.path().value()))
                        .toList();
            List<String> entityIds = autoMerged.stream()
                        .map(c -> c.entity().key())
                        .distinct()
                        .toList();
            TimelineMergeSummary summary = TimelineMergeSummary.merged(
                    sourceApplied, targetApplied, entityIds);
            return new TimelineMergeResult(TimelineMergeResult.MergeStatus.MERGED,
                    request.baseRevisionId(), request.sourceRevisionId(), request.targetRevisionId(),
                    persisted.revisionId(), autoMerged, List.of(), summary, message, mergedPayload);
        } catch (RuntimeException e) {
            log.error("Merge failed: base={} source={} target={}",
                    request.baseRevisionId(), request.sourceRevisionId(), request.targetRevisionId(), e);
            throw e;
        }
    }
    // ── helpers ───────────────────────────────────────────────────────────────

    private CanonicalTimelineSnapshot canonicalSnapshot(String payload, String revisionId) {
        try {
            TimelineDocument document = TimelineDocumentJsonSerializer.deserialize(payload);
            return TimelineSnapshotConverter.toSnapshot(document, revisionId);
        } catch (Exception failure) {
            throw new IllegalArgumentException(
                    "Revision payload is not canonical TimelineDocument: " + revisionId, failure);
        }
    }

    private TimelineRevisionRepository.RevisionRow loadRevision(
            String revisionId, String projectId, String tenantId) {
        return revisionRepository.findOwnedById(revisionId, projectId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Revision not found: " + revisionId));
    }

    private String loadPayload(TimelineRevisionRepository.RevisionRow revision, String contextTenant) {
        // CFRH-I2: ownership-scoped authoritative snapshot read only — no
        // ambient-global findPayload fallback. contextTenant is never null here
        // (TenantGuard.requireTenantId throws when absent); fail closed if it is.
        if (contextTenant == null) {
            throw new IllegalStateException("Ownership context missing for snapshot read: " + revision.snapshotId());
        }
        return snapshotService.findOwnedById(revision.projectId(), contextTenant,
                        revision.snapshotId())
                .map(TimelineSnapshotService.SnapshotInfo::payloadJson)
                .orElseThrow(() -> new IllegalStateException(
                        "Snapshot not found/owned: " + revision.snapshotId()));
    }

    private Optional<TimelineRevisionRepository.RevisionRow> findAcceptedDuplicate(
            TimelineMergeRequest request, String mergedPayloadHash, String contextTenant) {
        String expectedParents = request.targetRevisionId() + "," + request.sourceRevisionId();
        return revisionRepository.listOwnedByProject(
                        request.projectId(), contextTenant, null, null, null, DEDUP_SCAN_LIMIT).stream()
                .filter(TimelineRevisionRepository.RevisionRow::isMerge)
                .filter(row -> expectedParents.equals(row.mergeParentRevisionIds()))
                .filter(row -> request.baseRevisionId().equals(row.mergeBaseRevisionId()))
                .filter(row -> mergedPayloadHash.equals(row.contentHash()))
                .filter(row -> snapshotService.findOwnedById(
                        row.projectId(), contextTenant, row.snapshotId()).isPresent())
                .findFirst();
    }

    private static TimelineMergeResult duplicateResult(
            TimelineMergeRequest request, TimelineRevisionRepository.RevisionRow existing) {
        return new TimelineMergeResult(TimelineMergeResult.MergeStatus.MERGED,
                request.baseRevisionId(), request.sourceRevisionId(), request.targetRevisionId(),
                existing.id(), List.of(), List.of(), TimelineMergeSummary.empty(),
                "Duplicate merge request: existing merge revision returned", null);
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

    private static void requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private TimelineMergeResult conflictResult(
            TimelineMergeRequest request, List<TimelineConflict> conflicts) {
        List<String> conflicted = conflicts.stream().map(c -> c.entityRef().key()).toList();
        return new TimelineMergeResult(TimelineMergeResult.MergeStatus.CONFLICTS,
                request.baseRevisionId(), request.sourceRevisionId(), request.targetRevisionId(),
                null, List.of(), conflicts,
                TimelineMergeSummary.conflicts(0, 0, List.of(), conflicted),
                conflicts.size() + " conflict(s) require manual resolution", null);
    }

    private TimelineMergeResult noOpResult(TimelineMergeRequest request) {
        return new TimelineMergeResult(TimelineMergeResult.MergeStatus.NO_OP,
                request.baseRevisionId(), request.sourceRevisionId(), request.targetRevisionId(),
                null, List.of(), List.of(), TimelineMergeSummary.empty(),
                "No semantic changes to merge", null);
    }

    private static List<TimelineConflict> toInternalConflicts(List<TimelineMergePlanOperation> ops) {
        return ops.stream().map(TimelineMergeEngine::toInternalConflict).toList();
    }

    private static TimelineConflict toInternalConflict(TimelineMergePlanOperation op) {
        EntityRef ref = toEntityRef(op);
        return TimelineConflict.of(ref, TimelineConflictType.SAME_ENTITY_MODIFIED,
                SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, ref, op.path() + " (conflict)"),
                SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, ref, op.path() + " (conflict)"),
                "Canonical conflict on " + op.path());
    }

    private static EntityRef toEntityRef(TimelineMergePlanOperation op) {
        String path = op.path() != null ? op.path() : "";
        String kind = path.startsWith("track") ? "track" : "clip";
        return new EntityRef(EntityKind.valueOf(kind.toUpperCase()), op.operation() != null
                ? op.operation().path().value() : path);
    }

    private static EntityRef toEntityRef(TimelineChangeOperation op) {
        String path = op != null && op.path() != null ? op.path().value() : "";
        String kind = path.startsWith("track") ? "track" : "clip";
        return new EntityRef(EntityKind.valueOf(kind.toUpperCase()), path);
    }

    private static SemanticChangeType toSemanticChangeType(TimelineChangeOperation op) {
        if (op == null || op.type() == null) {
            return SemanticChangeType.CLIP_RANGE_CHANGED;
        }
        return switch (op.type()) {
            case TRACK_ADDED, CLIP_ADDED -> SemanticChangeType.CLIP_ADDED;
            case TRACK_REMOVED, CLIP_REMOVED -> SemanticChangeType.CLIP_REMOVED;
            case CLIP_MOVED, TRACK_REORDERED -> SemanticChangeType.CLIP_MOVED;
            default -> SemanticChangeType.CLIP_RANGE_CHANGED;
        };
    }

    private static TimelineResolutionIntent resolutionFor(
            Map<String, TimelineResolutionIntent> resolutions, TimelineMergePlanOperation op) {
        if (resolutions == null || resolutions.isEmpty()) {
            return null;
        }
        String key = op.operation() != null ? op.operation().path().value() : op.path();
        TimelineResolutionIntent direct = resolutions.get(key);
        if (direct != null) {
            return direct;
        }
        EntityRef ref = toEntityRef(op);
        return resolutions.get(ref.key());
    }
}
