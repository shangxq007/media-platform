package com.example.platform.identity.authorization;

import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.authorization.CanonicalActorResolver;
import java.util.Optional;

/**
 * Produces an explicit {@link CanonicalActor} of type {@link ActorType#SYSTEM} for
 * internal platform contexts (schedulers, workers, dispatchers, admin jobs).
 *
 * <p>This resolver is NOT a default/fallback. It is only consulted when code
 * explicitly operates within a known system context and requests a system actor.
 * The composite resolver never returns a SYSTEM actor, so an absent principal can
 * never be silently elevated to SYSTEM authority (AR-AUTH-002 / no
 * null-implies-system).</p>
 */
public class SystemCanonicalActorResolver implements CanonicalActorResolver {

    private final String systemActorId;
    private final String tenantId;

    public SystemCanonicalActorResolver(String systemActorId, String tenantId) {
        this.systemActorId = systemActorId;
        this.tenantId = tenantId;
    }

    @Override
    public Optional<CanonicalActor> resolveCurrentActor() {
        return Optional.of(CanonicalActor.system(systemActorId, tenantId));
    }

    /**
     * Factory used by infrastructure code to build a resolver for a given system job.
     */
    public static SystemCanonicalActorResolver forService(String serviceId, String tenantId) {
        return new SystemCanonicalActorResolver("system:" + serviceId, tenantId);
    }
}
