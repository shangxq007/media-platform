package com.example.platform.identity.authorization;

import com.example.platform.shared.authorization.ActorType;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.authorization.CanonicalActorResolver;
import com.example.platform.shared.logging.TraceKeys;
import com.example.platform.shared.web.TenantContext;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Resolves a {@link CanonicalActor} from the MDC {@code principal}/{@code tenantId},
 * which is populated by the platform filters for both JWT subjects and API-key
 * principals (see {@code JwtAuthFilter}, {@code OAuth2RequestContextFilter},
 * {@code ApiKeyAuthFilter}).
 *
 * <p>Falls back to {@link TenantContext} when the MDC tenant is absent. Returns
 * {@link Optional#empty()} when no principal is present. It never fabricates a
 * SYSTEM actor — the absence of a principal means "unauthenticated", not "system".</p>
 */
@Component
public class MdcCanonicalActorResolver implements CanonicalActorResolver {

    @Override
    public Optional<CanonicalActor> resolveCurrentActor() {
        String principal = MDC.get(TraceKeys.PRINCIPAL);
        if (principal == null || principal.isBlank() || "system".equals(principal)) {
            return Optional.empty();
        }
        String tenantId = MDC.get(TraceKeys.TENANT_ID);
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = TenantContext.get();
        }
        ActorType type = MDC.get("request.source") != null
                && "API_KEY".equalsIgnoreCase(MDC.get("request.source"))
                ? ActorType.API_KEY_PRINCIPAL
                : ActorType.USER;
        return Optional.of(new CanonicalActor(principal, type, tenantId, Set.of(), "mdc"));
    }
}
