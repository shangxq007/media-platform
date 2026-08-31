package com.example.platform.web.render;

import com.example.platform.shared.authorization.AuthorizableResourceRef;
import com.example.platform.shared.authorization.AuthorizationAction;
import com.example.platform.shared.authorization.AuthorizationContext;
import com.example.platform.shared.authorization.AuthorizationDecisionPort;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.shared.authorization.AuthorizationResourceType;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.authorization.CanonicalActorResolver;
import com.example.platform.shared.web.TenantContext;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fail-closed preparation boundary shared by every active Timeline HTTP surface.
 * Authorization is completed before a controller may hydrate or disclose project data,
 * and the returned actor is the only canonical author source.
 */
@Service
public final class TimelineProjectAuthorizationService {

    private static final AuthorizationAction READ = new AuthorizationAction(
            "READ", AuthorizationResourceType.PROJECT, "Read canonical Timeline");
    private static final AuthorizationAction WRITE = new AuthorizationAction(
            "WRITE", AuthorizationResourceType.PROJECT, "Mutate canonical Timeline");

    private final AuthorizationDecisionPort authorizationPort;
    private final CanonicalActorResolver actorResolver;

    public TimelineProjectAuthorizationService(
            AuthorizationDecisionPort authorizationPort,
            CanonicalActorResolver actorResolver) {
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort");
        this.actorResolver = Objects.requireNonNull(actorResolver, "actorResolver");
    }

    public CanonicalActor requireRead(String tenantId, String projectId) {
        return require(tenantId, projectId, READ);
    }

    public CanonicalActor requireWrite(String tenantId, String projectId) {
        return require(tenantId, projectId, WRITE);
    }

    private CanonicalActor require(
            String explicitTenantId, String projectId, AuthorizationAction action) {
        requireText(explicitTenantId, "tenantId");
        requireText(projectId, "projectId");
        String ambientTenantId = TenantContext.get();
        if (!explicitTenantId.equals(ambientTenantId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "explicit and ambient tenant must match");
        }
        CanonicalActor actor = actorResolver.resolveCurrentActor()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "authenticated actor required"));
        if (!explicitTenantId.equals(actor.tenantId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "authenticated actor tenant must match request tenant");
        }
        authorizationPort.requireAuthorized(new AuthorizationRequest(
                actor,
                action,
                new AuthorizableResourceRef(
                        AuthorizationResourceType.PROJECT,
                        projectId,
                        explicitTenantId,
                        projectId,
                        null),
                new AuthorizationContext(
                        "timeline-http", projectId,
                        Map.of("canonicalAuthorSource", "authenticated-actor"))));
        return actor;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, field + " required");
        }
    }
}
