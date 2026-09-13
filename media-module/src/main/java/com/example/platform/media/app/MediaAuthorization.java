package com.example.platform.media.app;
import com.example.platform.shared.authorization.*;
import com.example.platform.identity.api.authorization.*;
import com.example.platform.shared.web.TenantGuard;
import java.util.Map;
import org.springframework.stereotype.Component;
@Component
public class MediaAuthorization {
    private final CanonicalActorResolver actors;
    private final AuthorizationDecisionPort decisions;
    public MediaAuthorization(CanonicalActorResolver actors, AuthorizationDecisionPort decisions) {
        this.actors = actors; this.decisions = decisions;
    }
    public void require(String tenant, String project, boolean write) {
        TenantGuard.assertSameTenant(tenant);
        if (project == null || project.isBlank()) throw new IllegalArgumentException("project required");
        var actor = actors.resolveCurrentActor().orElseThrow(() -> new SecurityException("authenticated media actor required"));
        if (!tenant.equals(actor.tenantId())) throw new SecurityException("media actor tenant mismatch");
        decisions.requireAuthorized(new AuthorizationRequest(actor,
                new AuthorizationAction(write ? "WRITE" : "READ", AuthorizationResourceType.PROJECT, "Media asset"),
                new AuthorizableResourceRef(AuthorizationResourceType.PROJECT, project, tenant, project, null),
                new AuthorizationContext("media-asset", project, Map.of())));
    }
}
