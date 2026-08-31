package com.example.platform.render.testsupport;

import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.shared.authorization.AuthorizationDecisionPort;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.timeline.app.TimelineMutationContext;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.revisioncommand.RevisionRef;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.version.TimelineRevision;
import java.util.List;
import java.util.Set;

/** Explicit authenticated actor and policy wiring for Render integration tests. */
public final class TimelineMutationTestSupport {

    public static final AuthorizationDecisionPort ALLOW_ALL =
            request -> AuthorizationDecision.allow("test-explicit-policy");

    private TimelineMutationTestSupport() {
    }

    public static TimelineMutationContext user(
            String tenantId, String projectId, String actorId) {
        return new TimelineMutationContext(
                tenantId,
                projectId,
                CanonicalActor.user(actorId, tenantId, Set.of(), "test-authenticated"));
    }

    public static TimelineRevision save(
            TimelineRevisionSaveService service, String projectId, String expectedHead,
            TimelineDocument document, String actorId) {
        return save(service, TenantContext.get(), projectId, expectedHead, document, actorId);
    }

    public static TimelineRevision save(
            TimelineRevisionSaveService service, String tenantId, String projectId,
            String expectedHead, TimelineDocument document, String actorId) {
        return service.saveRevision(user(tenantId, projectId, actorId), expectedHead, document);
    }

    public static TimelineRevision saveWithEffects(
            TimelineRevisionSaveService service, String projectId, String expectedHead,
            TimelineDocument document, List<EffectInstance> effects,
            List<EffectInstance.EffectDefinition> definitions, String actorId) {
        return saveWithEffects(service, TenantContext.get(), projectId, expectedHead,
                document, effects, definitions, actorId);
    }

    public static TimelineRevision saveWithEffects(
            TimelineRevisionSaveService service, String tenantId, String projectId,
            String expectedHead, TimelineDocument document, List<EffectInstance> effects,
            List<EffectInstance.EffectDefinition> definitions, String actorId) {
        return service.saveRevisionWithEffects(
                user(tenantId, projectId, actorId), expectedHead, document, effects, definitions);
    }

    public static TimelineRevision restore(
            TimelineRevisionSaveService service, String projectId, String historicalRevisionId,
            String expectedHead, String actorId) {
        return restore(service, TenantContext.get(), projectId, historicalRevisionId,
                expectedHead, actorId);
    }

    public static TimelineRevision restore(
            TimelineRevisionSaveService service, String tenantId, String projectId,
            String historicalRevisionId, String expectedHead, String actorId) {
        return service.restoreRevision(
                user(tenantId, projectId, actorId), historicalRevisionId, expectedHead);
    }

    public static TimelineRevisionSaveService.RevisionWriteResult saveForCommand(
            TimelineRevisionSaveService service, RevisionRef ref, String expectedHead,
            TimelineDocument document, String actorId,
            TimelineRevisionSaveService.RevisionWriteCommand command) {
        return service.saveRevisionForCommand(
                user(ref.tenantId(), ref.projectId(), actorId),
                ref, expectedHead, document, command);
    }

    public static TimelineRevisionSaveService.RevisionWriteResult recordNoOp(
            TimelineRevisionSaveService service, RevisionRef ref, String expectedHead,
            String contentHash, TimelineRevisionSaveService.RevisionWriteCommand command) {
        return service.recordNoOpCommand(
                user(ref.tenantId(), ref.projectId(), "editor"),
                ref, expectedHead, contentHash, command);
    }

    public static com.example.platform.timeline.diff.merge.TimelineMergeRequest mergeRequest(
            String projectId, String tenantId, String baseRevisionId,
            String sourceRevisionId, String targetRevisionId,
            String actorId, String message) {
        return new com.example.platform.timeline.diff.merge.TimelineMergeRequest(
                user(tenantId, projectId, actorId),
                baseRevisionId, sourceRevisionId, targetRevisionId, message);
    }
}
