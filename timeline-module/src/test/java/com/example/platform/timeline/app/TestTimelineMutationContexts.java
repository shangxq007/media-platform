package com.example.platform.timeline.app;

import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.shared.authorization.AuthorizationDecisionPort;
import com.example.platform.shared.authorization.CanonicalActor;
import java.util.Set;

final class TestTimelineMutationContexts {

    static final AuthorizationDecisionPort ALLOW_ALL =
            request -> AuthorizationDecision.allow("test-explicit-policy");

    private TestTimelineMutationContexts() {
    }

    static TimelineMutationContext user(
            String tenantId, String projectId, String actorId) {
        return new TimelineMutationContext(
                tenantId,
                projectId,
                CanonicalActor.user(actorId, tenantId, Set.of(), "test-authenticated"));
    }

    static com.example.platform.timeline.diff.merge.TimelineMergeRequest mergeRequest(
            String projectId, String tenantId, String baseRevisionId,
            String sourceRevisionId, String targetRevisionId,
            String actorId, String message) {
        return new com.example.platform.timeline.diff.merge.TimelineMergeRequest(
                user(tenantId, projectId, actorId),
                baseRevisionId, sourceRevisionId, targetRevisionId, message);
    }
}
