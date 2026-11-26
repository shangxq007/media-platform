package com.example.platform.operation.application;

import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.operation.batch.BatchCommitResult;
import com.example.platform.operation.batch.BatchErrorCode;
import com.example.platform.operation.batch.BatchException;
import com.example.platform.operation.batch.BatchInvocationPort;
import com.example.platform.operation.batch.BatchPreview;
import com.example.platform.fonttext.typography.SubtitleBlockLayout;
import com.example.platform.operation.operation.OperationParameters.SubtitleCueParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.batch.OperationBatchContext;
import com.example.platform.operation.batch.OperationBatchPlan;
import com.example.platform.operation.batch.OperationBatchPlanner;
import com.example.platform.operation.batch.OperationBatchRequest;
import com.example.platform.operation.plan.AuthorizationDecision;
import com.example.platform.shared.authorization.AuthorizationAction;
import com.example.platform.shared.authorization.AuthorizationContext;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.shared.authorization.AuthorizationResourceType;
import com.example.platform.shared.authorization.AuthorizableResourceRef;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.timeline.canonical.TimelineDocument;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Application boundary for the generic batch port: project/tenant authorization
 * followed by the generic ordered planner and the Timeline domain commit.
 *
 * <p>This is the only batch entry service; REST (and future agents) call the
 * {@link BatchInvocationPort} contract instead of re-implementing planning or
 * commit semantics.
 */
@Service
public final class CanonicalBatchInvocationService implements BatchInvocationPort {

    private static final AuthorizationAction TIMELINE_EDIT = new AuthorizationAction(
            "WRITE", AuthorizationResourceType.PROJECT, "Edit canonical Timeline");
    private static final AuthorizationAction TIMELINE_READ = new AuthorizationAction(
            "READ", AuthorizationResourceType.PROJECT, "Read canonical Timeline for batch planning");

    private final TimelineBatchTargetDomain domain;
    private final SubtitleFontPolicy subtitleFonts;
    private final AuthorizationDecisionPort authorizationPort;
    private final OperationBatchPlanner planner = new OperationBatchPlanner();

    public CanonicalBatchInvocationService(TimelineBatchTargetDomain domain,
                                           AuthorizationDecisionPort authorizationPort) {
        this(domain, authorizationPort, new SubtitleFontPolicy(SubtitleBlockLayout.PROFILE + ","
                + SubtitleBlockLayout.SERIF_PROFILE, SubtitleBlockLayout.PROFILE, ""));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CanonicalBatchInvocationService(TimelineBatchTargetDomain domain, AuthorizationDecisionPort authorizationPort,
                                           SubtitleFontPolicy subtitleFonts) {
        this.subtitleFonts = Objects.requireNonNull(subtitleFonts, "subtitleFonts");
        this.domain = Objects.requireNonNull(domain, "domain");
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort");
    }

    @Override
    public BatchPreview preview(OperationBatchRequest request, OperationBatchContext context) {
        requireTenantContext(request, context.actor());
        requireProjectAccess(request, context.actor(), TIMELINE_READ);
        var resolved = resolveSubtitleFont(request, null);
        OperationBatchPlan<TimelineDocument> plan = planner.plan(resolved, domain);
        if (resolved.operations().getFirst().parameters() instanceof SubtitleCueParameters cue) {
            var font = SubtitleBlockLayout.profile(cue.motion().effectiveFontProfile());
            return BatchPreview.of(plan, new BatchPreview.ResolvedSubtitleFont(font.id(), font.label()));
        }
        return BatchPreview.of(plan);
    }

    @Override
    public BatchCommitResult commit(OperationBatchRequest request, OperationBatchContext context) {
        if (context.applyCommandId() == null || context.applyCommandId().isBlank()) {
            throw new BatchException(BatchErrorCode.AUTHORIZATION_CONTEXT_MISMATCH, null,
                    "applyCommandId required for batch commit");
        }
        requireTenantContext(request, context.actor());
        requireProjectAccess(request, context.actor(), TIMELINE_READ);
        var resolved = resolveSubtitleFont(request, context);
        OperationBatchPlan<TimelineDocument> plan = planner.plan(resolved, domain);
        if (!plan.batchDigest().equals(context.expectedBatchDigest())
                && resolved.operations().getFirst().parameters() instanceof SubtitleCueParameters cue
                && domain.completedBatchDocument(request, context).isPresent()) {
            // N18 before the authoring catalog treated a missing field as
            // explicit Sans. Check that old plan version only on an existing
            // exact receipt. New commands always bind the choice separately.
            resolved = replaceCue(resolved, cue.asLegacyReceipt());
            plan = planner.plan(resolved, domain);
        }

        if (!plan.batchDigest().equals(context.expectedBatchDigest())) {
            throw new BatchException(BatchErrorCode.PLAN_CHANGED, null, "Confirmed plan differs from submitted batch");
        }
        var securityDecision = authorizationPort.decide(new AuthorizationRequest(
                context.actor(),
                TIMELINE_EDIT,
                new AuthorizableResourceRef(AuthorizationResourceType.PROJECT,
                        request.target().projectId(), request.target().tenantId(),
                        request.target().projectId(), null),
                new AuthorizationContext("timeline-batch", null,
                        Map.of("batchDigest", plan.batchDigest()))));
        String policyRef = securityDecision.ruleRef() == null || securityDecision.ruleRef().isBlank()
                ? securityDecision.reasonCode() : securityDecision.ruleRef();
        AuthorizationDecision bound = securityDecision.allowed()
                ? AuthorizationDecision.allow(plan.batchDigest(), context.actor().actorId(),
                        request.target().projectId(), request.target().tenantId(),
                        request.target().refId(), policyRef)
                : AuthorizationDecision.deny(plan.batchDigest(), context.actor().actorId(),
                        request.target().projectId(), request.target().tenantId(),
                        request.target().refId(), policyRef);
        if (!bound.allowed()) {
            throw new BatchException(BatchErrorCode.AUTHORIZATION_DENIED, null,
                    "project batch write access denied");
        }
        OperationBatchContext authorized = OperationBatchContext.apply(
                context.applyCommandId(), context.actor(), bound, context.correlationId(), context.expectedBatchDigest());
        return domain.commit(resolved, plan, authorized);
    }

    private OperationBatchRequest resolveSubtitleFont(OperationBatchRequest request, OperationBatchContext applying) {
        var operations = request.operations();
        if (!(operations.getFirst().parameters() instanceof SubtitleCueParameters cue)) return request;
        if (operations.size() != 1) {
            throw new BatchException(BatchErrorCode.UNSUPPORTED_BATCH_OPERATION, 0,
                    "Submit the complete subtitle edit as one operation");
        }
        String profile = null;
        boolean previouslyOmittedFade = false;
        if (applying != null) {
            // Only the existing, fully scoped immutable receipt can carry an old
            // policy decision across a restart. The normal planner/digest, write
            // authorization and durable writer verify the request and principal.
            var previous = domain.completedBatchDocument(request, applying);
            if (previous.isPresent() && cue.useDefault()) profile = cueFont(previous.get(), cue.cueId());
            if (previous.isPresent() && !cue.useDefault()) profile = cue.motion().effectiveFontProfile();
            if (cue.fadeIntent() == SubtitleCueParameters.FadeIntent.PRESERVE && previous.isPresent()) {
                String cueId = cue.cueId();
                previouslyOmittedFade = previous.get().getTextElements().stream()
                        .anyMatch(element -> element.id().value().equals(cueId) && element.subtitleCue() != null
                                && element.subtitleCue().selectedTextFade() == null);
            }
        }
        if (profile == null) {
            var base = domain.resolveBase(request.target(), request.baseRevisionId(), request.baseContentHash());
            try {
                var saved = base.getTextElements().stream().filter(e -> e.id().value().equals(cue.cueId()) && e.subtitleCue() != null)
                        .findFirst();
                if (!cue.fontRunsSupplied() && saved.isPresent() && !saved.get().subtitleCue().fontRuns().isEmpty()) {
                    throw new IllegalArgumentException("Include the saved subtitle font runs or explicitly clear them with an empty list");
                }
                for (var run : cue.motion().fontRuns()) {
                    // Allow an existing withdrawn pin to remain on its unchanged text.
                    boolean retained = saved.filter(element -> element.subtitleCue().fontRuns().stream().anyMatch(old ->
                            old.fontProfile().equals(run.fontProfile()) && old.start() <= run.start() && old.end() >= run.end()
                            && element.styledText().content().substringByScalars(run.start(), run.end()).equals(
                                cue.text().substring(cue.text().offsetByCodePoints(0, run.start()), cue.text().offsetByCodePoints(0, run.end())))))
                            .isPresent();
                    if (!retained) subtitleFonts.requireSelectable(run.fontProfile());
                }
                profile = subtitleFonts.resolve(cue.useDefault() ? null : cue.motion().effectiveFontProfile(),
                        base.getMetadata().subtitleTrackLanguage(), cueFont(base, cue.cueId()));
            } catch (IllegalArgumentException unavailable) {
                if (applying == null) throw unavailable;
                throw new BatchException(BatchErrorCode.PLAN_CHANGED, 0, "Subtitle font availability changed. Preview again.");
            }
        }
        var resolved = cue.withResolvedFont(profile);
        return replaceCue(request, previouslyOmittedFade ? resolved.asCompletedFadeOmission() : resolved);
    }

    private static OperationBatchRequest replaceCue(OperationBatchRequest request, SubtitleCueParameters cue) {
        var source = request.operations().getFirst();
        var step = new OperationRequest(source.definitionId(), source.version(), source.target(),
                cue, source.baseRevisionId(), source.baseContentHash(), source.requestMetadata());
        return new OperationBatchRequest(request.batchId(), request.target(), request.baseRevisionId(),
                List.of(step), request.message());
    }

    private static String cueFont(TimelineDocument document, String cueId) {
        return document.getTextElements().stream()
                .filter(element -> element.id().value().equals(cueId) && element.subtitleCue() != null)
                .map(element -> element.subtitleCue().effectiveFontProfile()).findFirst().orElse(null);
    }

    private void requireTenantContext(OperationBatchRequest request, CanonicalActor actor) {
        if (actor.tenantId() == null || actor.tenantId().isBlank()
                || !actor.tenantId().equals(request.target().tenantId())) {
            throw new BatchException(BatchErrorCode.AUTHORIZATION_CONTEXT_MISMATCH, null,
                    "authenticated actor tenant must equal the batch target tenant");
        }
    }

    private void requireProjectAccess(OperationBatchRequest request, CanonicalActor actor,
                                      AuthorizationAction action) {
        var decision = authorizationPort.decide(new AuthorizationRequest(
                actor,
                action,
                new AuthorizableResourceRef(AuthorizationResourceType.PROJECT,
                        request.target().projectId(), request.target().tenantId(),
                        request.target().projectId(), null),
                new AuthorizationContext("timeline-batch-prepare", null,
                        Map.of("operation", "OPERATION_BATCH"))));
        if (!decision.allowed()) {
            throw new BatchException(BatchErrorCode.AUTHORIZATION_DENIED, null,
                    "project batch access denied");
        }
    }
}
