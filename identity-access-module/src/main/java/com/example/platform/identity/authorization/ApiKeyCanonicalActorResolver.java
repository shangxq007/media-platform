package com.example.platform.identity.authorization;

import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.shared.authorization.CanonicalActor;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Reads only the request attribute established by Identity's successful API-key authentication. */
@Component
@Order(100)
public final class ApiKeyCanonicalActorResolver implements CanonicalActorResolver {
    public static final String AUTHENTICATED_ACTOR_ATTRIBUTE = ApiKeyCanonicalActorResolver.class.getName() + ".actor";
    @Override public Optional<CanonicalActor> resolveCurrentActor() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return Optional.empty();
        Object actor = attributes.getRequest().getAttribute(AUTHENTICATED_ACTOR_ATTRIBUTE);
        return actor instanceof CanonicalActor authenticated ? Optional.of(authenticated) : Optional.empty();
    }
}
