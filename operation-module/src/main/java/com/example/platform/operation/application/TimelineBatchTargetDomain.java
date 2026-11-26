package com.example.platform.operation.application;

import com.example.platform.operation.batch.BatchCommitResult;
import com.example.platform.operation.batch.BatchErrorCode;
import com.example.platform.operation.batch.BatchException;
import com.example.platform.operation.batch.BatchStepOutcome;
import com.example.platform.operation.batch.BatchTargetDomain;
import com.example.platform.operation.batch.OperationBatchContext;
import com.example.platform.operation.batch.OperationBatchDigest;
import com.example.platform.operation.batch.OperationBatchPlan;
import com.example.platform.operation.batch.OperationBatchPlanner;
import com.example.platform.operation.batch.OperationBatchRequest;
import com.example.platform.operation.batch.VersionedAggregateRef;
import com.example.platform.operation.operation.OperationInstance;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationRequestResolver;
import com.example.platform.operation.operation.OperationResolutionException;
import com.example.platform.operation.operation.OperationTarget;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.operation.plan.AuthorizationDecision;
import com.example.platform.operation.plan.OperationPlan;
import com.example.platform.operation.plan.OperationPlanner;
import com.example.platform.operation.plan.PlanException;
import com.example.platform.timeline.api.composition.TimelineCanonicalRejectionException;
import com.example.platform.timeline.api.composition.TimelineSourceValidation;
import com.example.platform.timeline.api.composition.TimelineValidation;
import com.example.platform.timeline.api.revision.RevisionProvenance;
import com.example.platform.timeline.api.revision.TimelineMutationContext;
import com.example.platform.timeline.api.revision.TimelineRevisionCommandConflictException;
import com.example.platform.timeline.api.revision.TimelineRevisionCommands;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonicalmodel.TimelineDiagnostic;
import com.example.platform.timeline.canonicalmodel.TimelineValidationResult;
import com.example.platform.timeline.revisioncommand.RevisionRef;
import com.example.platform.timeline.version.TimelineConflictException;
import com.example.platform.timeline.version.TimelineRevision;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Timeline implementation of the generic batch domain seam.
 *
 * <p>It reuses the existing Operation resolver/planner, the Timeline canonical
 * validator/source validator and the Timeline revision authority. It creates no
 * second commit path: every successful batch ends in exactly one
 * {@link TimelineRevisionCommands} command execution.
 */
@Service
public final class TimelineBatchTargetDomain implements BatchTargetDomain<TimelineDocument> {

    public static final String AGGREGATE_KIND = "TIMELINE";
    public static final String BATCH_COMMAND_DOMAIN = "OPERATION_BATCH";

    private final TimelineRevisionCommands revisions;
    private final TimelineSourceValidation sourceValidator;
    private final TimelineValidation timelineValidator;
    private final TimelineContentDigester digester = new TimelineContentDigester();
    private final OperationPlanner planner = new OperationPlanner();
    private final OperationBatchPlanner batchPlanner = new OperationBatchPlanner();

    private final com.example.platform.operation.operation.TextOperationPlanner.FontResolutionInput fonts;
    private final com.example.platform.artifact.app.ArtifactSourceFactsQuery sourceFacts;

    public TimelineBatchTargetDomain(TimelineRevisionCommands revisions, TimelineSourceValidation sourceValidator,
            TimelineValidation timelineValidator) {
        this(revisions, sourceValidator, timelineValidator, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public TimelineBatchTargetDomain(TimelineRevisionCommands revisions,
                                     TimelineSourceValidation sourceValidator,
                                     TimelineValidation timelineValidator,
                                     com.example.platform.operation.operation.TextOperationPlanner.FontResolutionInput fonts,
                                     com.example.platform.artifact.app.ArtifactSourceFactsQuery sourceFacts) {
        this.fonts = fonts;
        this.sourceFacts = sourceFacts;
        this.revisions = Objects.requireNonNull(revisions, "revisions");
        this.sourceValidator = Objects.requireNonNull(sourceValidator, "sourceValidator");
        this.timelineValidator = Objects.requireNonNull(timelineValidator, "timelineValidator");
    }

    @Override
    public String aggregateKind() {
        return AGGREGATE_KIND;
    }

    @Override
    public TimelineDocument resolveBase(VersionedAggregateRef target, String baseRevisionId,
                                        String baseContentHash) {
        if (!AGGREGATE_KIND.equals(target.aggregateKind()) || !target.aggregateId().equals(target.projectId())) {
            throw new BatchException(BatchErrorCode.CROSS_AGGREGATE_BATCH_UNSUPPORTED, null,
                    "Timeline batch target must bind the project's timeline aggregate");
        }
        if (!RevisionRef.MAIN_REF.equals(target.refId())
                && revisions.findEditingRefHead(new RevisionRef(target.tenantId(), target.projectId(), target.refId())) == null) {
            throw new BatchException(BatchErrorCode.BASE_REVISION_NOT_FOUND, null,
                    "Editing branch not found in the target Timeline");
        }
        TimelineRevision revision = revisions.findById(target.tenantId(), baseRevisionId);
        if (revision == null || !target.projectId().equals(revision.productId())) {
            throw new BatchException(BatchErrorCode.BASE_REVISION_NOT_FOUND, null,
                    "base revision not found in the target Timeline");
        }
        String authoritativeHash = revision.semanticContext().timelineContentDigest();
        if (!authoritativeHash.equals(baseContentHash)) {
            throw new BatchException(BatchErrorCode.STALE_BASE_REVISION, null,
                    "base Timeline content hash mismatch");
        }
        return revisions.findPayloadDocument(target.tenantId(), baseRevisionId)
                .orElseThrow(() -> new BatchException(BatchErrorCode.BASE_REVISION_NOT_FOUND, null,
                        "base revision canonical payload unavailable"));
    }

    @Override
    public String digest(TimelineDocument state) {
        return digester.digest(state);
    }

    @Override
    public StepPlan<TimelineDocument> planStep(OperationBatchRequest batch, OperationRequest operation,
                                               TimelineDocument currentCandidate) {
        String projectId = batch.target().projectId();
        requireSameAggregate(operation, projectId);
        OperationInstance instance;
        try {
            instance = OperationRequestResolver.resolve(operation,
                    new OperationRequestResolver.OperationBaseContext(
                            batch.baseRevisionId(), batch.baseContentHash(), currentCandidate, projectId));
        } catch (OperationResolutionException resolution) {
            BatchErrorCode code = resolution.code()
                    == com.example.platform.operation.operation.OperationErrorCode.STALE_BASE_REVISION
                    ? BatchErrorCode.STALE_BASE_REVISION : BatchErrorCode.STEP_RESOLUTION_FAILED;
            throw new BatchException(code, null, resolution.getMessage(), resolution);
        } catch (IllegalArgumentException invalid) {
            throw new BatchException(BatchErrorCode.STEP_RESOLUTION_FAILED, null,
                    invalid.getMessage(), invalid);
        }
        if (instance.parameters() instanceof OperationParameters.PasteClipsParameters paste) {
            validatePaste(batch, paste, currentCandidate);
        }
        if (instance.parameters() instanceof OperationParameters.RollCutParameters
                || instance.parameters() instanceof OperationParameters.SlideParameters) {
            String edit = instance.parameters() instanceof OperationParameters.SlideParameters ? "Slide" : "Roll";
            if (!RevisionRef.MAIN_REF.equals(batch.target().refId())) {
                throw new BatchException(BatchErrorCode.REF_NOT_SUPPORTED, null, edit + " requires the default editing branch");
            }
            if (batch.operations().size() != 1) {
                throw new BatchException(BatchErrorCode.UNSUPPORTED_BATCH_OPERATION, null, edit + " must be the only operation in the batch");
            }
        }
        if (rippleDelete(operation) || instance.parameters() instanceof OperationParameters.OpacityParameters
                || instance.parameters() instanceof OperationParameters.SlipParameters) {
            String edit = rippleDelete(operation) ? "Ripple Delete" : instance.parameters() instanceof OperationParameters.OpacityParameters ? "Opacity" : "Slip";
            if (!RevisionRef.MAIN_REF.equals(batch.target().refId())) {
                throw new BatchException(BatchErrorCode.REF_NOT_SUPPORTED, null,
                        edit + " is only available on the default editing branch");
            }
            if (batch.operations().size() != 1
                    || !(operation.target() instanceof com.example.platform.operation.operation.OperationTargetRequest.ClipSelectionTargetRequest request)
                    || !(request.selectionSpec() instanceof com.example.platform.timeline.semantics.selection.SelectionSpec.ExplicitObjectSelection selection)
                    || selection.clipIds().size() != 1) {
                throw new BatchException(BatchErrorCode.UNSUPPORTED_BATCH_OPERATION, null,
                        edit + " requires one explicitly selected clip and must be the only operation in its batch");
            }
        }
        if (instance.parameters() instanceof OperationParameters.MoveParameters
                && instance.target() instanceof OperationTarget.ResolvedClipScopeTarget clips
                && clips.resolvedScope().resolvedClipIds().size() > 1) {
            if (!multiMove(operation)) {
                throw new BatchException(BatchErrorCode.UNSUPPORTED_BATCH_OPERATION, null, "Multi-clip move requires an explicit selection");
            }
            if (!RevisionRef.MAIN_REF.equals(batch.target().refId())) {
                throw new BatchException(BatchErrorCode.REF_NOT_SUPPORTED, null, "Multi-clip move is only available on the default editing branch");
            }
            if (batch.operations().size() != 1) {
                throw new BatchException(BatchErrorCode.UNSUPPORTED_BATCH_OPERATION, null, "Multi-clip move must be the only operation in its batch");
            }
        }
        validateSource(instance, batch, currentCandidate, projectId);
        final OperationPlan plan;
        try {
            if (instance.parameters() instanceof OperationParameters.RestoreRevisionParameters restore) {
                if (!RevisionRef.MAIN_REF.equals(batch.target().refId())) {
                    throw new BatchException(BatchErrorCode.REF_NOT_SUPPORTED, null,
                            "Restore is currently available on the default editing branch");
                }
                if (batch.operations().size() != 1) {
                    throw new BatchException(BatchErrorCode.UNSUPPORTED_BATCH_OPERATION, null,
                            "Restore requires a single-operation batch");
                }
                var base = requireRestoreRevision(batch, batch.baseRevisionId());
                var source = requireRestoreRevision(batch, restore.sourceRevisionId());
                plan = planner.planRestore(instance, source.document(), base.semanticDigest(), source.semanticDigest());
            } else {
                plan = planner.plan(instance, batch.baseRevisionId(), currentCandidate, fonts);
            }
        } catch (PlanException failure) {
            throw new BatchException(BatchErrorCode.STEP_PLAN_FAILED, null,
                    failure.getMessage(), failure);
        }
        if (instance.parameters() instanceof OperationParameters.SlideParameters slide) {
            validateMediaRanges(batch, plan.candidateTimeline(), slide.trackId(),
                    List.of(slide.leftClipId(), slide.middleClipId(), slide.rightClipId()), "Slide", true);
        }
        if (instance.parameters() instanceof OperationParameters.RollCutParameters roll) {
            validateMediaRanges(batch, plan.candidateTimeline(), roll.trackId(), List.of(roll.leftClipId(), roll.rightClipId()), "Roll", false);
        }
        if (instance.parameters() instanceof OperationParameters.SlipParameters
                && instance.target() instanceof OperationTarget.ResolvedClipScopeTarget scope) {
            var ids = scope.resolvedScope().resolvedClipIds();
            var track = plan.candidateTimeline().getTracks().stream()
                    .filter(t -> t.clips().stream().anyMatch(c -> c.getClipId().equals(ids.getFirst())))
                    .findFirst().orElseThrow();
            validateMediaRanges(batch, plan.candidateTimeline(), track.trackId(), ids, "Slip", true);
        }
        return new StepPlan<>(plan.candidateTimeline(), instance.parameterDigest(),
                targetIdentity(plan.sourceInstance().target()), plan.planDigest(), plan.plannedChanges().stream()
                        .map(change -> new com.example.platform.operation.batch.BatchStepDescriptor.Change(
                                com.example.platform.operation.plan.OperationPlanDigest.changeKey(change), change.primary()))
                        .toList(), instance.parameters() instanceof OperationParameters.RestoreRevisionParameters
                                ? plan.noOp() : null);
    }

    private TimelineRevisionCommands.RestoreSource requireRestoreRevision(OperationBatchRequest batch, String revisionId) {
        return revisions.findRestoreSource(batch.target().projectId(), batch.target().tenantId(), revisionId)
                .orElseThrow(() -> new BatchException(BatchErrorCode.BASE_REVISION_NOT_FOUND, null,
                        "Revision not found in the target Timeline"));
    }

    @Override
    public void validateStep(int index, TimelineDocument candidate, OperationBatchRequest batch) {
        TimelineValidationResult result = timelineValidator.validateDocument(
                batch.target().projectId(), candidate);
        if (result.hasFatalErrors()) {
            throw new BatchException(BatchErrorCode.STEP_VALIDATION_FAILED, index,
                    "candidate rejected at step " + index + ": " + diagnostics(result));
        }
    }

    private static boolean multiMove(OperationRequest operation) {
        return operation.parameters() instanceof OperationParameters.MoveParameters
                && operation.target() instanceof com.example.platform.operation.operation.OperationTargetRequest.ClipSelectionTargetRequest request
                && request.selectionSpec() instanceof com.example.platform.timeline.semantics.selection.SelectionSpec.ExplicitObjectSelection selection
                && selection.clipIds().size() > 1;
    }

    private static boolean rippleDelete(OperationRequest operation) {
        return operation.definitionId().equals(
                com.example.platform.operation.operation.OperationDefinition.V3.RIPPLE_DELETE.definitionId());
    }

    private static boolean preservesParentEffects(OperationBatchRequest batch) {
        return batch.operations().size() == 1 && (batch.operations().getFirst().parameters()
                instanceof OperationParameters.TrimParameters
                || batch.operations().getFirst().parameters() instanceof OperationParameters.TransitionCreateParameters
                || batch.operations().getFirst().parameters() instanceof OperationParameters.TransitionUpdateParameters
                || batch.operations().getFirst().parameters() instanceof OperationParameters.TransitionDeleteParameters
                || batch.operations().getFirst().parameters() instanceof OperationParameters.PasteClipsParameters
                || batch.operations().getFirst().parameters() instanceof OperationParameters.SlideParameters
                || batch.operations().getFirst().parameters() instanceof OperationParameters.OpacityParameters
                || batch.operations().getFirst().parameters() instanceof OperationParameters.SlipParameters
                || batch.operations().getFirst().parameters() instanceof OperationParameters.RollCutParameters
                || multiMove(batch.operations().getFirst()) || rippleDelete(batch.operations().getFirst()));
    }

    private void validatePaste(OperationBatchRequest batch, OperationParameters.PasteClipsParameters paste,
                               TimelineDocument candidate) {
        var target = batch.target();
        if (!RevisionRef.MAIN_REF.equals(target.refId())) {
            throw new BatchException(BatchErrorCode.REF_NOT_SUPPORTED, null, "Paste is only available on the default editing branch");
        }
        if (batch.operations().size() != 1) {
            throw new BatchException(BatchErrorCode.UNSUPPORTED_BATCH_OPERATION, null, "Paste must be the only operation in its batch");
        }
        try {
            revisions.validatePasteSources(new RevisionRef(target.tenantId(), target.projectId(), target.refId()),
                    batch.baseRevisionId(), paste.sourceClipIds());
        } catch (IllegalArgumentException invalid) {
            throw new BatchException(BatchErrorCode.STEP_RESOLUTION_FAILED, null, invalid.getMessage(), invalid);
        }
        validateMediaRanges(batch, candidate, paste.trackId(), paste.sourceClipIds(), "Paste", false);
    }

    private void validateMediaRanges(OperationBatchRequest batch, TimelineDocument candidate, String trackId,
                                     List<TimelineClipId> ids, String operation, boolean checkEmbeddedAudio) {
        validateMediaRanges(batch, candidate, trackId, ids, operation, checkEmbeddedAudio, null);
    }

    private void validateMediaRanges(OperationBatchRequest batch, TimelineDocument candidate, String trackId,
            List<TimelineClipId> ids, String operation, boolean checkEmbeddedAudio,
            com.example.platform.timeline.canonicalmodel.CanonicalTransition transition) {
        var target = batch.target();
        try {
            var track = candidate.getTracks().stream().filter(t -> t.trackId().equals(trackId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(operation + " track not found"));
            var clips = track.clips().stream().collect(java.util.stream.Collectors.toMap(
                    com.example.platform.timeline.canonical.TimelineClip::getClipId, clip -> clip));
            for (TimelineClipId id : ids) {
                var clip = clips.get(id);
                if (clip == null) throw new IllegalArgumentException(operation + " source is not on the copied track: " + id.value());
                if (!"MEDIA_STREAM".equals(clip.getSourceKind())) {
                    throw new IllegalArgumentException(operation + " requires a media-stream source: " + id.value());
                }
                var binding = new com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding(
                        com.example.platform.media.domain.identity.MediaAssetId.of(clip.getMediaAssetId()),
                        com.example.platform.media.domain.stream.MediaStreamId.of(clip.getMediaStreamId()),
                        new com.example.platform.shared.identity.ArtifactId(clip.getArtifactId()),
                        com.example.platform.shared.digest.ContentDigest.sha256(clip.getContentDigest()),
                        new com.example.platform.timeline.semantics.clip.MediaClip.TimeRange(clip.getTrimStart(),
                                transition != null && transition.outgoingClipId().equals(id.value())
                                        ? clip.getTrimEnd().add(transition.duration()) : clip.getTrimEnd()));
                boolean routedEmbeddedAudio = checkEmbeddedAudio && track.type() == com.example.platform.timeline.canonical.TrackType.VIDEO
                        && candidate.getAudioMix().routes().stream().anyMatch(route -> route.input().trackId().equals(trackId)
                                && route.input().clipId().equals(id.value()));
                var validation = routedEmbeddedAudio
                        ? sourceValidator.validateRoutedVideo(binding, target.tenantId(), target.projectId())
                        : sourceValidator.validate(binding, target.tenantId(), target.projectId(), track.type());
                if (!validation.valid()) {
                    throw new IllegalArgumentException(operation + " source " + id.value() + " is invalid: " + validation.violations());
                }
            }
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            throw new BatchException(BatchErrorCode.STEP_RESOLUTION_FAILED, null, invalid.getMessage(), invalid);
        }
    }

    @Override
    public void validateFinal(TimelineDocument candidate, OperationBatchRequest batch) {
        TimelineValidationResult result = timelineValidator.validateDocument(
                batch.target().projectId(), candidate);
        if (result.hasFatalErrors()) {
            throw new BatchException(BatchErrorCode.FINAL_VALIDATION_FAILED, null,
                    "final candidate rejected: " + diagnostics(result));
        }
        for (var transition : candidate.getTransitions()) {
            if (!transition.usesOutgoingHandle()) continue;
            var outgoing = TimelineClipId.of(transition.outgoingClipId());
            var incoming = TimelineClipId.of(transition.incomingClipId());
            var track = candidate.getTracks().stream().filter(t -> t.clips().stream()
                    .anyMatch(c -> c.getClipId().equals(outgoing))).findFirst().orElseThrow();
            validateMediaRanges(batch, candidate, track.trackId(), List.of(outgoing, incoming),
                    "Cross dissolve", false, transition);
        }
        if (!RevisionRef.MAIN_REF.equals(batch.target().refId()) || preservesParentEffects(batch)) {
            try {
                revisions.validateEditingBranchCandidate(new RevisionRef(batch.target().tenantId(),
                        batch.target().projectId(), batch.target().refId()), batch.baseRevisionId(), candidate);
            } catch (TimelineCanonicalRejectionException rejection) {
                throw new BatchException(BatchErrorCode.FINAL_VALIDATION_FAILED, null, rejection.getMessage(), rejection);
            }
        }
    }

    @Override
    public BatchCommitResult commit(OperationBatchRequest batch, OperationBatchPlan<TimelineDocument> plan,
                                    OperationBatchContext context) {
        String tenantId = batch.target().tenantId();
        String projectId = batch.target().projectId();
        verifyAuthorization(plan, context, projectId, tenantId);
        if (!plan.baseRevisionId().equals(batch.expectedHeadRevisionId())) {
            throw new BatchException(BatchErrorCode.STALE_BASE_REVISION, null,
                    "expected head must equal the batch base revision");
        }
        if (!digester.digest(plan.finalCandidate()).equals(plan.finalCandidateHash())) {
            throw new BatchException(BatchErrorCode.PLAN_CHANGED, null,
                    "batch plan candidate changed after preview");
        }
        // Plan reproducibility immediately before the single write: any drift in
        // steps, base or final candidate fails closed.
        OperationBatchPlan<TimelineDocument> verified = batchPlanner.plan(batch, this);
        if (!verified.batchDigest().equals(plan.batchDigest())
                || !verified.finalCandidateHash().equals(plan.finalCandidateHash())) {
            throw new BatchException(BatchErrorCode.PLAN_CHANGED, null,
                    "batch plan is not reproducible from the exact base");
        }

        TimelineRevisionCommands.RevisionWriteCommand command = new TimelineRevisionCommands.RevisionWriteCommand(
                context.applyCommandId(), plan.batchDigest(),
                OperationBatchDigest.fingerprint(plan, context),
                BATCH_COMMAND_DOMAIN, tenantId, preservesParentEffects(batch));
        TimelineMutationContext mutationContext =
                new TimelineMutationContext(tenantId, projectId, context.actor());
        RevisionRef targetRef = new RevisionRef(tenantId, projectId, batch.target().refId());
        try {
            if (plan.noOp()) {
                TimelineRevisionCommands.RevisionWriteResult result = revisions.recordNoOpCommand(mutationContext, targetRef,
                        plan.baseRevisionId(), plan.baseContentHash(), command);
                return BatchCommitResult.noOp(batch.batchId(), plan.batchDigest(),
                        plan.baseRevisionId(), result.timelineContentHash(),
                        batch.target().refId(), plan.outcomes(), result.replayed());
            }
            RevisionProvenance provenance = BatchCommitProvenance.of(plan, batch.message(), batch.operations());
            TimelineRevisionCommands.RevisionWriteResult result =
                    batch.operations().getFirst().parameters() instanceof OperationParameters.RestoreRevisionParameters restore
                    ? revisions.saveRestoreForCommand(mutationContext, targetRef, plan.baseRevisionId(),
                            restore.sourceRevisionId(), plan.finalCandidateHash(), command, provenance)
                    : revisions.saveRevisionForCommand(mutationContext, targetRef, plan.baseRevisionId(),
                            plan.finalCandidate(), command, provenance);
            if (!plan.finalCandidateHash().equals(result.timelineContentHash())) {
                throw new BatchException(BatchErrorCode.PERSISTENCE_FAILURE, null,
                        "canonical writer committed a different Timeline digest");
            }
            return BatchCommitResult.applied(batch.batchId(), plan.batchDigest(),
                    plan.baseRevisionId(), result.revisionId(), result.timelineContentHash(),
                    result.parentRevisionId(), batch.target().refId(), plan.outcomes(),
                    result.replayed());
        } catch (TimelineConflictException conflict) {
            throw new BatchException(BatchErrorCode.STALE_BASE_REVISION, null,
                    conflict.getMessage(), conflict);
        } catch (TimelineRevisionCommandConflictException conflict) {
            throw new BatchException(BatchErrorCode.COMMAND_ID_CONFLICT, null,
                    conflict.getMessage(), conflict);
        } catch (TimelineCanonicalRejectionException rejection) {
            throw new BatchException(BatchErrorCode.FINAL_VALIDATION_FAILED, null,
                    "canonical writer rejected the final candidate: "
                            + rejection.diagnostics().stream()
                                    .map(TimelineDiagnostic::message).toList(),
                    rejection);
        }
    }

    private void verifyAuthorization(OperationBatchPlan<TimelineDocument> plan,
                                     OperationBatchContext context, String projectId, String tenantId) {
        AuthorizationDecision authorization = context.authorization();
        if (authorization == null) {
            throw new BatchException(BatchErrorCode.AUTHORIZATION_CONTEXT_MISMATCH, null,
                    "authorized batch context required for commit");
        }
        if (!authorization.planDigest().equals(plan.batchDigest())) {
            throw new BatchException(BatchErrorCode.PLAN_CHANGED, null,
                    "authorization does not bind the exact batch digest");
        }
        if (!authorization.projectId().equals(projectId)
                || !authorization.tenantId().equals(tenantId)
                || !authorization.targetRefId().equals(plan.target().refId())
                || !authorization.principalRef().equals(context.principalRef())) {
            throw new BatchException(BatchErrorCode.AUTHORIZATION_CONTEXT_MISMATCH, null,
                    "authorization context does not match the batch apply context");
        }
        if (!authorization.allowed()) {
            throw new BatchException(BatchErrorCode.AUTHORIZATION_DENIED, null,
                    "authorization denied");
        }
    }

    private static void requireSameAggregate(OperationRequest operation, String projectId) {
        switch (operation.target()) {
            case OperationTargetRequest.TimelineTargetRequest target -> {
                if (!projectId.equals(target.timelineId())) {
                    throw new BatchException(BatchErrorCode.CROSS_AGGREGATE_BATCH_UNSUPPORTED, null,
                            "operation target is outside the batch aggregate");
                }
            }
            case OperationTargetRequest.ClipSelectionTargetRequest target -> {
                if (target.timelineId() != null && !projectId.equals(target.timelineId())) {
                    throw new BatchException(BatchErrorCode.CROSS_AGGREGATE_BATCH_UNSUPPORTED, null,
                            "operation target is outside the batch aggregate");
                }
            }
            // Group/Sync/Audio identities live inside the batch aggregate; only the batch
            // target selects the aggregate, so there is no second aggregate to compare.
            case OperationTargetRequest.GroupTargetRequest ignored -> {
            }
            case OperationTargetRequest.SyncTargetRequest ignored -> {
            }
            case OperationTargetRequest.AudioTargetRequest ignored -> {
            }
            case OperationTargetRequest.TransitionTargetRequest ignored -> {}
            case OperationTargetRequest.TextElementTargetRequest ignored -> {
            }
        }
    }

    private void validateSource(OperationInstance instance, OperationBatchRequest batch,
                                TimelineDocument currentCandidate, String projectId) {
        if (instance.parameters() instanceof OperationParameters.CompositionAspectParameters aspect && aspect.input() != null) {
            var pin = aspect.input();
            var observed = sourceFacts.find(batch.target().tenantId(), projectId,
                    new com.example.platform.shared.identity.ArtifactId(pin.artifactId()),
                    com.example.platform.shared.digest.ContentDigest.sha256(pin.contentDigest()))
                    .stream().flatMap(facts -> facts.streams().stream())
                    .filter(stream -> stream.kind() == com.example.platform.artifact.contract.ArtifactSourceFacts.Kind.VIDEO
                            && stream.streamId().equals(pin.streamId()))
                    .map(com.example.platform.artifact.contract.ArtifactSourceFacts.Stream::displayAspectRatio)
                    .filter(Objects::nonNull)
                    .map(com.example.platform.timeline.canonical.CompositionAspectRatio::fromKey)
                    .limit(2).toList();
            if (observed.size() != 1 || !observed.getFirst().equals(aspect.aspectRatio())) {
                throw new BatchException(BatchErrorCode.STEP_RESOLUTION_FAILED, null, "Source display aspect unavailable or mismatched");
            }
        }
        if (!(instance.parameters() instanceof OperationParameters.AddMediaClipParameters add)) {
            return;
        }
        TimelineTrack track = currentCandidate.getTracks().stream()
                .filter(candidate -> candidate.trackId().equals(add.trackId()))
                .findFirst()
                .orElseThrow(() -> new BatchException(BatchErrorCode.STEP_RESOLUTION_FAILED, null,
                        "target track does not exist: " + add.trackId()));
        TimelineSourceValidation.ValidationResult validation = sourceValidator.validate(
                add.sourceBinding(), batch.target().tenantId(), projectId, track.type());
        if (!validation.valid()) {
            throw new BatchException(BatchErrorCode.STEP_RESOLUTION_FAILED, null,
                    "source reference invalid: " + String.join("; ", validation.violations()));
        }
    }

    private static String targetIdentity(OperationTarget target) {
        return switch (target) {
            case OperationTarget.TimelineTarget timeline -> "timeline:" + timeline.timelineId();
            case OperationTarget.ResolvedClipScopeTarget scope ->
                    "clips:" + scope.resolvedScope().resolvedClipIds().stream()
                            .map(TimelineClipId::value).toList();
            case OperationTarget.GroupTarget group -> "group:" + group.groupId().value();
            case OperationTarget.SyncTarget sync -> "sync:" + sync.syncIdentityKey();
            case OperationTarget.TransitionTarget t -> "transition:" + t.transitionId();
            case OperationTarget.TextElementTarget text -> "text:" + text.id().value();
            case OperationTarget.AudioTarget audio -> "audio:" + audio.audioMixInput();
        };
    }

    private static String diagnostics(TimelineValidationResult result) {
        List<String> messages = result.diagnostics().stream()
                .map(diagnostic -> diagnostic.code().name() + ":" + diagnostic.message())
                .toList();
        return String.join("; ", messages);
    }
}
