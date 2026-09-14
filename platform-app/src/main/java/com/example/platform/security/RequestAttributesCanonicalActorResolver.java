package com.example.platform.security;

import com.example.platform.shared.authorization.ActorType;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.shared.web.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves a {@link CanonicalActor} from the {@code jwt.*} request attributes set by
 * the platform security filters ({@code JwtAuthFilter}, {@code OAuth2RequestContextFilter})
 * plus the {@code request.source} attribute.
 *
 * <p>Lives in platform-app because it depends on {@code spring-web}
 * ({@code RequestContextHolder}) and reads the attributes populated by the platform
 * security filters here. Identity publishes the interface so consumers including
 * Workflow use the canonical actor through its {@link CanonicalActorResolver} port.</p>
 *
 * <p>Returns {@link Optional#empty()} when no authenticated subject is present — it never
 * fabricates a SYSTEM actor from a missing principal.</p>
 */
@Component
@org.springframework.core.annotation.Order(0)
public class RequestAttributesCanonicalActorResolver implements CanonicalActorResolver {

    private final com.example.platform.identity.api.account.AccountIdentityQueries memberships;
    public RequestAttributesCanonicalActorResolver(com.example.platform.identity.api.account.AccountIdentityQueries memberships) {
        this.memberships = memberships;
    }

    public static final String ATTR_SUBJECT = "jwt.subject";
    public static final String ATTR_TENANT = "jwt.tenantId";
    public static final String ATTR_ROLES = "jwt.roles";
    public static final String ATTR_SOURCE = "request.source";

    @Override
    public Optional<CanonicalActor> resolveCurrentActor() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return Optional.empty();
        }
        HttpServletRequest request = attributes.getRequest();
        Object subject = request.getAttribute("auth.subject");
        if (subject == null || subject.toString().isBlank()) {
            return Optional.empty();
        }
        String tenantId = attr(request, ATTR_TENANT);
        String issuer = attr(request, "jwt.issuer");
        var membership = memberships.resolve(issuer, subject.toString(), tenantId);
        return Optional.of(new CanonicalActor(membership.membershipId(), ActorType.USER,
                membership.tenantId(), Set.of(membership.role()), "verified-issuer-subject", membership.accountId()));
    }

    private static String attr(HttpServletRequest request, String name) {
        Object v = request.getAttribute(name);
        return v == null ? null : v.toString();
    }
}
