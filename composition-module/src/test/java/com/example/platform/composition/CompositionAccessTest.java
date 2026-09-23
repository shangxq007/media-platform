package com.example.platform.composition;

import com.example.platform.composition.app.CompositionAccess;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.identity.api.workspace.*;
import com.example.platform.shared.authorization.CanonicalActor;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompositionAccessTest {
    @Test void rejectsStaleSessionAndAmbiguousMembership() {
        var actors=mock(CanonicalActorResolver.class); var workspaces=mock(WorkspaceQueries.class);
        when(actors.resolveCurrentActor()).thenReturn(Optional.of(CanonicalActor.user("u","t",Set.of(),"test")));
        when(workspaces.listWorkspacesForUser("u")).thenReturn(List.of(
                new WorkspaceResponse("w","t","W",null,null,"ACTIVE",null,null)));
        when(workspaces.listMembers("w")).thenReturn(List.of(
                new WorkspaceMemberResponse("m1","w","u","EDITOR","ACTIVE",null,null),
                new WorkspaceMemberResponse("m2","w","u","EDITOR","ACTIVE",null,null)));
        var access=new CompositionAccess(actors,workspaces);
        assertThrows(RuntimeException.class,()->access.resolve("w"));
    }

    @Test void rejectsCrossTenantAndInactiveWorkspace() {
        var actors=mock(CanonicalActorResolver.class); var workspaces=mock(WorkspaceQueries.class);
        when(actors.resolveCurrentActor()).thenReturn(Optional.of(CanonicalActor.user("u","t",Set.of(),"test")));
        when(workspaces.listWorkspacesForUser("u")).thenReturn(List.of(new WorkspaceResponse("w","other","W",null,null,"ACTIVE",null,null)));
        assertThrows(RuntimeException.class,()->new CompositionAccess(actors,workspaces).resolve("w"));
    }
}
