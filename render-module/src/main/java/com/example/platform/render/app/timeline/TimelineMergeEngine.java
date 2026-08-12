package com.example.platform.render.app.timeline;

import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineCanonicalNormalizer;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineCanonicalValidator;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineValidationResult;
import com.example.platform.render.domain.timeline.semantics.time.FrameRate;
import com.example.platform.render.domain.timeline.semantics.time.MediaTime;
import com.example.platform.render.domain.timeline.diff.TimelineChangeOperation;
import com.example.platform.render.domain.timeline.diff.TimelinePatch;
import com.example.platform.render.domain.timeline.diff.TimelinePatchId;
import com.example.platform.render.domain.timeline.diff.application.TimelinePatchApplicationResult;
import com.example.platform.render.domain.timeline.diff.application.TimelinePatchApplicationStatus;
import com.example.platform.render.domain.timeline.diff.application.TimelinePatchApplier;
import com.example.platform.render.domain.timeline.diff.calculation.CanonicalTimelineClipSnapshot;
import com.example.platform.render.domain.timeline.diff.calculation.CanonicalTimelineSnapshot;
import com.example.platform.render.domain.timeline.diff.calculation.CanonicalTimelineTrackSnapshot;
import com.example.platform.render.domain.timeline.diff.calculation.TimelineSnapshotConverter;
import com.example.platform.render.domain.timeline.diff.merge.plan.TimelineMergePlanOperation;
import com.example.platform.render.domain.timeline.diff.merge.plan.TimelineMergePlanOperationStatus;
import com.example.platform.render.domain.timeline.diff.merge.plan.TimelineMergePlanOperationSource;
import com.example.platform.render.domain.timeline.diff.merge.plan.TimelineMergePlanRequest;
import com.example.platform.render.domain.timeline.diff.merge.plan.TimelineMergePlanRequestId;
import com.example.platform.render.domain.timeline.diff.merge.plan.TimelineMergePlanPolicy;
import com.example.platform.render.domain.timeline.diff.merge.plan.TimelineNonConflictingMergePlan;
import com.example.platform.render.domain.timeline.diff.merge.plan.TimelineNonConflictingMergePlanner;
import com.example.platform.render.domain.timeline.diff.merge.preview.TimelineMergePreviewRequest;
import com.example.platform.render.domain.timeline.diff.merge.preview.TimelineMergePreviewRequestId;
import com.example.platform.render.domain.timeline.diff.merge.preview.TimelineMergePreviewMode;
import com.example.platform.render.domain.timeline.diff.merge.preview.TimelineMergePreviewPolicy;
import com.example.platform.render.domain.timeline.diff.merge.preview.TimelineMergePreviewService;
import com.example.platform.render.domain.timeline.internal.EntityKind;
import com.example.platform.render.domain.timeline.internal.EntityRef;
import com.example.platform.render.domain.timeline.internal.SemanticChange;
import com.example.platform.render.domain.timeline.internal.SemanticChangeType;
import com.example.platform.render.domain.timeline.internal.TimelineConflict;
import com.example.platform.render.domain.timeline.internal.TimelineConflictType;
import com.example.platform.render.domain.timeline.internal.TimelineMergeRequest;
import com.example.platform.render.domain.timeline.internal.TimelineMergeResult;
import com.example.platform.render.domain.timeline.internal.TimelineMergeSummary;
import com.example.platform.render.domain.timeline.internal.TimelineResolutionIntent;
import com.example.platform.render.domain.timeline.version.TimelineConflictException;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.ErrorCode;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.shared.web.TenantGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private final ProductCurrentRevisionService currentRevisionService;
    private final TimelineMergePreviewService previewService;
    private final TimelineNonConflictingMergePlanner mergePlanner;
    private final TimelinePatchApplier patchApplier;
    private final ObjectMapper objectMapper;

    public TimelineMergeEngine(
            TimelineRevisionRepository revisionRepository,
            TimelineSnapshotService snapshotService,
            ProductCurrentRevisionService currentRevisionService,
            TimelineMergePreviewService previewService,
            TimelineNonConflictingMergePlanner mergePlanner,
            TimelinePatchApplier patchApplier,
            ObjectMapper objectMapper) {
        this.revisionRepository = revisionRepository;
        this.snapshotService = snapshotService;
        this.currentRevisionService = currentRevisionService;
        this.previewService = previewService;
        this.mergePlanner = mergePlanner;
        this.patchApplier = patchApplier;
        this.objectMapper = objectMapper;
    }

    public TimelineMergeResult merge(TimelineMergeRequest request) {
        return merge(request, Map.of());
    }

    @Transactional
    public TimelineMergeResult merge(
            TimelineMergeRequest request,
            Map<String, TimelineResolutionIntent> resolutions) {
        try {
            String contextTenant = TenantGuard.requireTenantId();
            String effectiveTenant = request.effectiveTenant();
            if (effectiveTenant == null || effectiveTenant.isBlank()) {
                effectiveTenant = contextTenant;
            } else {
                TenantGuard.assertSameTenant(effectiveTenant);
            }
            requireNotBlank(request.projectId(), "projectId");
            requireNotBlank(request.baseRevisionId(), "baseRevisionId");
            requireNotBlank(request.sourceRevisionId(), "sourceRevisionId");
            requireNotBlank(request.targetRevisionId(), "targetRevisionId");

            var baseRevision = loadRevision(request.baseRevisionId());
            var sourceRevision = loadRevision(request.sourceRevisionId());
            var targetRevision = loadRevision(request.targetRevisionId());

            assertProjectAndTenant(request.projectId(), contextTenant, baseRevision);
            assertProjectAndTenant(request.projectId(), contextTenant, sourceRevision);
            assertProjectAndTenant(request.projectId(), contextTenant, targetRevision);

            String basePayload = loadPayload(baseRevision, contextTenant);
            String sourcePayload = loadPayload(sourceRevision, contextTenant);
            String targetPayload = loadPayload(targetRevision, contextTenant);

            // Canonical gate (C1 authority): internal-1.0 payload -> TimelineCandidate,
            // validated + normalized. Always enabled (C1-CRR1: no bypass flag).
            TimelineCandidate baseCandidate = canonicalGate(request.projectId(), basePayload);
            TimelineCandidate sourceCandidate = canonicalGate(request.projectId(), sourcePayload);
            TimelineCandidate targetCandidate = canonicalGate(request.projectId(), targetPayload);

            // Single bounded conversion path (C1-CRR1): canonical persisted payload
            // -> semantic merge snapshot via the gate's own candidate model.
            CanonicalTimelineSnapshot baseSnapshot =
                    TimelineSnapshotConverter.toSnapshot(baseCandidate, request.baseRevisionId());
            CanonicalTimelineSnapshot sourceSnapshot =
                    TimelineSnapshotConverter.toSnapshot(sourceCandidate, request.sourceRevisionId());
            CanonicalTimelineSnapshot targetSnapshot =
                    TimelineSnapshotConverter.toSnapshot(targetCandidate, request.targetRevisionId());

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
            if (application.status() != com.example.platform.render.domain.timeline.diff.application.TimelinePatchApplicationStatus.APPLIED
                    && application.status() != com.example.platform.render.domain.timeline.diff.application.TimelinePatchApplicationStatus.NO_OP) {
                return new TimelineMergeResult(TimelineMergeResult.MergeStatus.FAILED,
                        request.baseRevisionId(), request.sourceRevisionId(), request.targetRevisionId(),
                        null, List.of(), List.of(), TimelineMergeSummary.empty(),
                        "Canonical merge application failed", null);
            }

            CanonicalTimelineSnapshot mergedSnapshot = application.patchedSnapshot();
            // C1-CRR1: merged payload is rebuilt in the canonical persisted format
            // (internal-1.0). The target JSON's document-level fields are preserved;
            // composition.tracks are replaced by the merged semantic tracks so the
            // persisted merged revision passes the canonical gate and remains
            // consumable by the same save/load/merge authority.
            String mergedPayload = toInternalPayload(targetPayload, mergedSnapshot, request.targetRevisionId());

            canonicalGate(request.projectId(), mergedPayload);

            // Frozen idempotency + stale-current contract (unchanged from legacy engine).
            String mergedPayloadHash = computeMergeHash(
                    request.sourceRevisionId(), request.targetRevisionId(), mergedPayload);
            Optional<TimelineRevisionRepository.RevisionRow> existing =
                    findAcceptedDuplicate(request, mergedPayloadHash, contextTenant);
            if (existing.isPresent()) {
                return duplicateResult(request, existing.get());
            }
            String currentId = currentRevisionService.getCurrentRevisionId(request.projectId());
            if (currentId != null && !request.targetRevisionId().equals(currentId)) {
                throw new TimelineConflictException(
                        request.projectId(), request.targetRevisionId(), currentId);
            }

            String message = request.message() != null ? request.message()
                    : "Merge " + request.sourceRevisionId() + " into " + request.targetRevisionId();

            String snapshotId = snapshotService.save(
                    request.projectId(), effectiveTenant, mergedPayload, "internal-1.0");
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
            log.info("Canonical merge revision created: id={} project={} rev={}",
                    mergeRevisionId, request.projectId(), revNum);

            currentRevisionService.updateCurrentRevision(
                    request.projectId(), request.targetRevisionId(), mergeRevisionId);

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
                    mergeRevisionId, autoMerged, List.of(), summary, message, mergedPayload);
        } catch (RuntimeException e) {
            log.error("Merge failed: base={} source={} target={}",
                    request.baseRevisionId(), request.sourceRevisionId(), request.targetRevisionId(), e);
            throw e;
        }
    }
    // ── helpers (frozen contract preserved from legacy authority) ─────────────────

    /**
     * Rebuild the merged revision payload in the canonical persisted format
     * (internal-1.0). Document-level fields of the target payload (id, name,
     * project, assetRegistry, output, extensions, metadata) are preserved;
     * {@code composition.tracks} are replaced by the merged semantic tracks
     * (ms -> exact frame @ target rate).
     *
     * <p>C1-CRR1 frozen contract: the merged payload must pass the canonical
     * gate (which it does — {@code merge} re-gates it immediately) and remain
     * consumable by the same save/load/merge authority on the next merge.</p>
     */
    private String toInternalPayload(String targetPayload, CanonicalTimelineSnapshot mergedSnapshot,
                                     String revisionId) {
        try {
            JsonNode targetRoot = InternalTimelineJson.parse(targetPayload);
            if (!InternalTimelineJson.isInternalTimeline(targetRoot)) {
                throw new IllegalStateException("Merge target payload is not internal-1.0");
            }
            ObjectNode composition = (ObjectNode) targetRoot.path("composition");
            if (composition == null || composition.isMissingNode()) {
                throw new IllegalStateException("Merge target payload has no composition block");
            }
            ObjectNode mergedRoot = (ObjectNode) InternalTimelineJson.mapper().valueToTree(targetRoot);
            ObjectNode mergedComposition = (ObjectNode) mergedRoot.path("composition");
            // C1-CNM1: merged tracks carry each clip's exact rational rate and
            // opaque effect payload; no single target-fps projection is applied.
            mergedComposition.set("tracks", tracksToJson(mergedSnapshot.tracks()));
            // revision counter is a document-level field; the persistence layer
            // re-assigns it on insert, so leave it untouched here.
            return InternalTimelineJson.write(mergedRoot);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to rebuild internal-1.0 merged payload", e);
        }
    }

    private ArrayNode tracksToJson(List<CanonicalTimelineTrackSnapshot> tracks) {
        ObjectMapper mapper = InternalTimelineJson.mapper();
        ArrayNode out = mapper.createArrayNode();
        // Track order is semantic (TRACK_REORDERED materializes the order field);
        // emit in order, mirroring TimelineSnapshotConverter.toDocument sorting.
        List<CanonicalTimelineTrackSnapshot> ordered = new ArrayList<>(tracks);
        ordered.sort(java.util.Comparator.comparingInt(CanonicalTimelineTrackSnapshot::order));
        for (CanonicalTimelineTrackSnapshot track : ordered) {
            ObjectNode trackNode = mapper.createObjectNode();
            trackNode.put("id", track.trackId());
            String type = track.kind() != null ? track.kind() : "VIDEO";
            trackNode.put("type", type);
            ArrayNode clips = mapper.createArrayNode();
            for (CanonicalTimelineClipSnapshot clip : track.clips()) {
                clips.add(clipToJson(clip, mapper));
            }
            trackNode.set("clips", clips);
            out.add(trackNode);
        }
        return out;
    }

    private ObjectNode clipToJson(CanonicalTimelineClipSnapshot clip, ObjectMapper mapper) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", clip.clipId());
        node.put("assetId", clip.assetBindingId());
        node.set("timelineRange", rangeToJson(clip.start(), clip.duration(), clip.rate(), mapper));
        node.set("sourceRange", rangeToJson(clip.sourceStart(), clip.sourceDuration(), clip.rate(), mapper));
        // C1-CNM1 effect preservation: merged clips carry their opaque effect
        // payloads (preserved target/source-side, never semantically merged).
        if (clip.effects() != null && !clip.effects().isEmpty()) {
            ArrayNode effects = mapper.createArrayNode();
            for (com.example.platform.render.domain.timeline.canonicalmodel.TimelineClipEffect fx : clip.effects()) {
                ObjectNode fxNode = mapper.createObjectNode();
                if (fx.id() != null) {
                    fxNode.put("id", fx.id());
                }
                fxNode.put("effectKey", fx.effectKey());
                if (fx.parameters() != null && !fx.parameters().isEmpty()) {
                    fxNode.set("parameters", mapper.valueToTree(fx.parameters()));
                }
                effects.add(fxNode);
            }
            node.set("effects", effects);
        }
        return node;
    }

    /**
     * C1-CNM1: exact rational time -> frame @ exact rational rate.
     *
     * <p>{@code MediaTime.toFrameExact(rate)} computes
     * frame = ticks * rate.num / (timeScale * rate.den) exactly (BigInteger).
     * For canonical frame-derived values this is the exact inverse — no
     * quantization, no integer-ms step, no denominator loss. A non-frame-
     * aligned merged value throws (canonical merge output must never silently
     * quantize); the merge machinery only ever produces frame-aligned values
     * from frame-aligned inputs.</p>
     */
    private ObjectNode rangeToJson(MediaTime start, MediaTime duration, FrameRate rate, ObjectMapper mapper) {
        ObjectNode rateNode = mapper.createObjectNode();
        rateNode.put("num", rate.numerator());
        rateNode.put("den", rate.denominator());
        ObjectNode startNode = mapper.createObjectNode();
        startNode.put("frame", start.toFrameExact(rate));
        startNode.set("rate", rateNode);
        ObjectNode durationNode = mapper.createObjectNode();
        durationNode.put("frame", duration.toFrameExact(rate));
        durationNode.set("rate", rateNode);
        ObjectNode range = mapper.createObjectNode();
        range.set("start", startNode);
        range.set("duration", durationNode);
        return range;
    }

    private TimelineCandidate canonicalGate(String projectId, String internalTimelineJson) {
        TimelineCandidate candidate = InternalTimelineCandidateAdapter.map(projectId, internalTimelineJson);
        TimelineValidationResult validation = TimelineCanonicalValidator.validate(candidate);
        if (validation.hasFatalErrors()) {
            throw new TimelineCanonicalRejectionException(validation.diagnostics());
        }
        TimelineCanonicalNormalizer.normalize(candidate)
                .orElseThrow(() -> new TimelineCanonicalRejectionException(validation.diagnostics()));
        return candidate;
    }

    private TimelineRevisionRepository.RevisionRow loadRevision(String revisionId) {
        return revisionRepository.findById(revisionId)
                .orElseThrow(() -> new IllegalArgumentException("Revision not found: " + revisionId));
    }

    private String loadPayload(TimelineRevisionRepository.RevisionRow revision, String contextTenant) {
        if (contextTenant != null) {
            TimelineSnapshotService.SnapshotInfo info = snapshotService.findById(revision.snapshotId())
                    .orElseThrow(() -> new IllegalStateException("Snapshot not found: " + revision.snapshotId()));
            TenantGuard.assertSameTenant(info.tenantId());
            return info.payloadJson();
        }
        return snapshotService.findPayload(revision.snapshotId())
                .orElseThrow(() -> new IllegalStateException("Snapshot not found: " + revision.snapshotId()));
    }

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
        return new TimelineMergeResult(TimelineMergeResult.MergeStatus.MERGED,
                request.baseRevisionId(), request.sourceRevisionId(), request.targetRevisionId(),
                existing.id(), List.of(), List.of(), TimelineMergeSummary.empty(),
                "Duplicate merge request: existing merge revision returned", null);
    }

    private static String computeMergeHash(String sourceId, String targetId, String payload) {
        String input = "merge:" + sourceId + ":" + targetId + ":" + (payload != null ? payload : "");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
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
