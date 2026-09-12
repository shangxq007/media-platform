package com.example.platform.social.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.shared.authorization.AuthorizationDecisionPort;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.authorization.CanonicalActorResolver;
import com.example.platform.social.api.dto.PublicationAccountResponse;
import com.example.platform.social.infrastructure.persistence.ConnectedPlatformRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SocialAccountReadServiceTest {

    @Mock private CanonicalActorResolver actorResolver;
    @Mock private AuthorizationDecisionPort authorizationPort;
    @Mock private SocialProjectScopePort projectScopePort;
    @Mock private ConnectedPlatformRepository accountRepository;

    private SocialAccountReadService service;

    @BeforeEach
    void setUp() {
        service = new SocialAccountReadService(
                actorResolver, authorizationPort, projectScopePort, accountRepository);
    }

    @Test
    void returnsExactAuthorizedIdentityDisplayProjectScopeAndBindingVersion() {
        CanonicalActor actor = CanonicalActor.user("actor-1", "tenant-a", Set.of(), "jwt");
        when(actorResolver.resolveCurrentActor()).thenReturn(Optional.of(actor));
        when(authorizationPort.decide(any())).thenReturn(AuthorizationDecision.allow("RBAC"));
        when(projectScopePort.belongsToTenant("tenant-a", "project-1")).thenReturn(true);
        when(accountRepository.findPublicationAccounts("tenant-a", "actor-1", "project-1")).thenReturn(List.of(
                account("account-1", "channel name", "ACTIVE", 9L),
                account("inactive", "old", "DISCONNECTED", 3L)));

        List<PublicationAccountResponse> response = service.list("tenant-a", "project-1");

        assertEquals(1, response.size());
        PublicationAccountResponse account = response.getFirst();
        assertEquals("account-1", account.id());
        assertEquals("channel name", account.displayName());
        assertEquals("YOUTUBE", account.platformType());
        assertEquals("project-1", account.projectId());
        assertEquals(9L, account.bindingVersion());
        assertEquals(PublicationAccountResponse.EndpointAccess.AUTHORIZED_LOCAL_PROJECT_ACCOUNT_READ,
                account.endpointAccess());
        assertEquals(PublicationAccountResponse.GlobalEffectiveAccess.UNKNOWN_FAIL_CLOSED,
                account.globalEffectiveAccess());

        InOrder order = inOrder(authorizationPort, projectScopePort, accountRepository);
        order.verify(authorizationPort).decide(any());
        order.verify(projectScopePort).belongsToTenant("tenant-a", "project-1");
        order.verify(accountRepository).findPublicationAccounts("tenant-a", "actor-1", "project-1");
    }

    @Test
    void missingDisplayIsNotHardcodedOrReplacedWithProviderIdentity() {
        allowProject();
        when(accountRepository.findPublicationAccounts("tenant-a", "actor-1", "project-1"))
                .thenReturn(List.of(account("account-1", null, "ACTIVE", 2L)));

        PublicationAccountResponse response = service.list("tenant-a", "project-1").getFirst();

        assertNull(response.displayName());
        assertEquals(PublicationAccountResponse.DisplayNameAvailability.NOT_PROVIDED,
                response.displayNameAvailability());
    }

    @Test
    void denialStopsBeforeProjectOrAccountHydration() {
        when(actorResolver.resolveCurrentActor()).thenReturn(Optional.of(
                CanonicalActor.user("actor-1", "tenant-a", Set.of(), "jwt")));
        when(authorizationPort.decide(any())).thenReturn(
                AuthorizationDecision.deny("RBAC_DENY", "RBAC"));

        assertThrows(ResponseStatusException.class, () -> service.list("tenant-a", "project-1"));

        verifyNoInteractions(projectScopePort, accountRepository);
    }

    private void allowProject() {
        when(actorResolver.resolveCurrentActor()).thenReturn(Optional.of(
                CanonicalActor.user("actor-1", "tenant-a", Set.of(), "jwt")));
        when(authorizationPort.decide(any())).thenReturn(AuthorizationDecision.allow("RBAC"));
        when(projectScopePort.belongsToTenant("tenant-a", "project-1")).thenReturn(true);
    }

    private static SocialAccountReadModel account(
            String id, String displayName, String status, long bindingVersion) {
        return new SocialAccountReadModel(id, displayName, "YOUTUBE", status, bindingVersion);
    }
}
