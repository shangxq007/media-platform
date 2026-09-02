package com.example.platform.render.testsupport;

import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.authorization.CanonicalActorResolver;
import com.example.platform.shared.events.RenderInitiator;
import java.util.Optional;
import java.util.Set;

public final class RenderInitiatorFixtures {
    private RenderInitiatorFixtures() {}

    public static RenderInitiator user(String tenantId) {
        return user("test-principal-p1", tenantId);
    }

    public static RenderInitiator user(String actorId, String tenantId) {
        return RenderInitiator.from(CanonicalActor.user(actorId, tenantId, Set.of(), "test"));
    }

    public static RenderInitiator system(String tenantId) {
        return RenderInitiator.from(CanonicalActor.system("test-render-system", tenantId));
    }

    public static CanonicalActorResolver resolver(String tenantId) {
        return () -> Optional.of(CanonicalActor.user(
                "test-principal-p1", tenantId, Set.of(), "test"));
    }
}
