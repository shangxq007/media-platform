package com.example.platform.identity.authorization;

import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.authorization.CanonicalActorResolver;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * The primary {@link CanonicalActorResolver} used at HTTP boundaries.
 *
 * <p>Delegates to the ordered list of resolvers (request-attributes first, then MDC).
 * The first non-empty result wins. The {@link SystemCanonicalActorResolver} is
 * deliberately NOT part of this chain, so an unauthenticated request yields
 * {@link Optional#empty()} rather than an implicit SYSTEM actor.</p>
 */
@Component
@Primary
public class CompositeCanonicalActorResolver implements CanonicalActorResolver {

    private final List<CanonicalActorResolver> delegates;

    public CompositeCanonicalActorResolver(List<CanonicalActorResolver> resolvers) {
        this.delegates = resolvers.stream()
                .filter(r -> !(r instanceof CompositeCanonicalActorResolver))
                .filter(r -> !(r instanceof SystemCanonicalActorResolver))
                .toList();
    }

    @Override
    public Optional<CanonicalActor> resolveCurrentActor() {
        for (CanonicalActorResolver delegate : delegates) {
            Optional<CanonicalActor> resolved = delegate.resolveCurrentActor();
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }
}
