package com.example.platform.security;

import com.example.platform.shared.authorization.ActorType;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.authorization.CanonicalActorResolver;
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
 * security filters here. The interface lives in shared-kernel so any module (including
 * workflow-module, which cannot depend on identity-access or platform-app) can consume
 * the canonical actor through the {@link CanonicalActorResolver} port.</p>
 *
 * <p>Returns {@link Optional#empty()} when no authenticated subject is present — it never
 * fabricates a SYSTEM actor from a missing principal.</p>
 */
@Component
public class RequestAttributesCanonicalActorResolver implements CanonicalActorResolver {

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
        Object subject = request.getAttribute(ATTR_SUBJECT);
        if (subject == null || subject.toString().isBlank()) {
            return Optional.empty();
        }
        String actorId = subject.toString();
        String tenantId = attr(request, ATTR_TENANT);
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = TenantContext.get();
        }
        Set<String> roles = rolesOf(request.getAttribute(ATTR_ROLES));
        String source = attr(request, ATTR_SOURCE);
        String authSource = source != null ? source.toLowerCase() : "jwt";
        return Optional.of(new CanonicalActor(actorId, ActorType.USER, tenantId, roles, authSource));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> rolesOf(Object rolesAttr) {
        if (rolesAttr instanceof List<?> list) {
            List<String> converted = new ArrayList<>();
            for (Object o : list) {
                if (o != null) {
                    converted.add(o.toString());
                }
            }
            return Set.copyOf(converted);
        }
        if (rolesAttr instanceof Set<?> set) {
            return set.stream().map(Object::toString).collect(java.util.stream.Collectors.toSet());
        }
        return Set.of();
    }

    private static String attr(HttpServletRequest request, String name) {
        Object v = request.getAttribute(name);
        return v == null ? null : v.toString();
    }
}
