package com.example.platform.delivery.app;

import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.shared.authorization.*;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Delivery entry-point checks; all permission decisions belong to the existing RBAC port. */
@Component
public class DeliveryAccess {
    private final CanonicalActorResolver actors;
    private final AuthorizationDecisionPort authorization;
    private final DeliveryProjectScopePort projects;

    public DeliveryAccess(CanonicalActorResolver actors, AuthorizationDecisionPort authorization,
                          DeliveryProjectScopePort projects) {
        this.actors = actors;
        this.authorization = authorization;
        this.projects = projects;
    }

    public void require(String tenantId, String projectId, boolean mutation) {
        CanonicalActor actor = actor();
        if (actor.isSystem() || !tenantId.equals(actor.tenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Delivery tenant access denied");
        }
        var type = projectId == null ? AuthorizationResourceType.TENANT : AuthorizationResourceType.PROJECT;
        var action = new AuthorizationAction(mutation ? "delivery.manage" : "delivery.read", type,
                mutation ? "Manage delivery" : "Read delivery");
        var decision = authorization.decide(new AuthorizationRequest(actor, action,
                new AuthorizableResourceRef(type, projectId, tenantId, projectId, null),
                new AuthorizationContext("delivery", null, Map.of())));
        if (!decision.allowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Delivery permission denied");
        }
        if (projectId != null && !projects.belongsToTenant(tenantId, projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
    }

    public void requireAdministrator() {
        CanonicalActor actor = actor();
        // Same ADMIN role used by the platform's authenticated /api/admin security chain.
        if (actor.isSystem() || !(actor.roles().contains("ADMIN") || actor.roles().contains("ROLE_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator required");
        }
    }

    private CanonicalActor actor() {
        return actors.resolveCurrentActor().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated actor required"));
    }
}
