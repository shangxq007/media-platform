package com.example.platform.composition.app;

import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.identity.api.workspace.WorkspaceQueries;
import com.example.platform.shared.authorization.ActorType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Revalidates persisted membership through Identity on every operation. */
@Component
public class CompositionAccess {
    private final CanonicalActorResolver actors;
    private final WorkspaceQueries workspaces;

    public CompositionAccess(CanonicalActorResolver actors, WorkspaceQueries workspaces) {
        this.actors = actors;
        this.workspaces = workspaces;
    }

    public record Scope(String tenantId, String workspaceId, String actorId) {}

    public Scope resolve(String selection) {
        var actor = actors.resolveCurrentActor().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (actor.actorType() != ActorType.USER || actor.tenantId() == null) throw denied();
        var candidates = workspaces.listWorkspacesForUser(actor.actorId()).stream()
                .filter(w -> actor.tenantId().equals(w.tenantId()) && "ACTIVE".equals(w.status()))
                .filter(w -> selection == null || selection.equals(w.id())).toList();
        if (candidates.size() != 1) throw denied();
        var workspace = candidates.getFirst();
        var memberships = workspaces.listMembers(workspace.id()).stream()
                .filter(m -> actor.actorId().equals(m.userId())).toList();
        if (memberships.size() != 1 || !"ACTIVE".equals(memberships.getFirst().status())) throw denied();
        return new Scope(actor.tenantId(), workspace.id(), actor.actorId());
    }

    public Scope require(String tenantHint, String workspaceSelection) {
        var scope = resolve(workspaceSelection);
        if (!scope.tenantId().equals(tenantHint)) throw denied();
        return scope;
    }
    public String currentTenant() {
        var actor=actors.resolveCurrentActor().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if(actor.actorType()!=ActorType.USER || actor.tenantId()==null) throw denied();
        return actor.tenantId();
    }

    private static ResponseStatusException denied() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Workspace unavailable");
    }
}
