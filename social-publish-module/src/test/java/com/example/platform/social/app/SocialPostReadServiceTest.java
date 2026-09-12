package com.example.platform.social.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.shared.authorization.AuthorizationDecisionPort;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.authorization.CanonicalActorResolver;
import com.example.platform.social.api.dto.PublicationPostListResponse;
import com.example.platform.social.api.dto.PublicationPostResponse;
import com.example.platform.social.infrastructure.persistence.ConnectedPlatformRepository;
import com.example.platform.social.infrastructure.persistence.SocialPostRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SocialPostReadServiceTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-02-01T00:00:00Z");

    @Mock private CanonicalActorResolver actorResolver;
    @Mock private AuthorizationDecisionPort authorizationPort;
    @Mock private SocialProjectScopePort projectScopePort;
    @Mock private ConnectedPlatformRepository accountRepository;
    @Mock private SocialPostRepository postRepository;

    private SocialPostReadService service;

    @BeforeEach
    void setUp() {
        service = new SocialPostReadService(
                actorResolver, authorizationPort, projectScopePort, accountRepository, postRepository);
    }

    @Test
    void listAuthorizesBeforeHydrationAndUsesExactCurrentAccountBinding() {
        CanonicalActor actor = allowProject("actor-1");
        when(accountRepository.findPublicationAccount(
                "tenant-a", "actor-1", "project-1", "account-1", 7L))
                .thenReturn(Optional.of(account("account-1", "actor-1", 7L, "ACTIVE")));
        when(postRepository.findReadProjection(
                "tenant-a", "actor-1", "project-1", "account-1", 7L, START, END, 25))
                .thenReturn(List.of(new SocialPostReadModel(
                        "post-1", "project-1", "account-1", 7L,
                        "hello", "artifact-1", "YOUTUBE", START)));

        PublicationPostListResponse response = service.list(
                "tenant-a", "project-1", "account-1", 7L, START, END, 25);

        assertEquals(PublicationPostListResponse.Coverage.BOUNDED_PARTIAL, response.coverage());
        PublicationPostResponse item = response.items().getFirst();
        assertEquals("hello", item.contentText());
        assertEquals("project-1", item.projectId());
        assertEquals("account-1", item.connectedAccountId());
        assertEquals(7L, item.bindingVersion());
        assertEquals("artifact-1", item.artifactId());
        assertEquals(PublicationPostResponse.TimeMeaning.PLANNED_PUBLISH_TIME, item.timeMeaning());
        assertEquals(PublicationPostResponse.EndpointAccess.AUTHORIZED_LOCAL_PROJECT_ACCOUNT_READ,
                item.endpointAccess());
        assertEquals(PublicationPostResponse.GlobalEffectiveAccess.UNKNOWN_FAIL_CLOSED,
                item.globalEffectiveAccess());

        ArgumentCaptor<AuthorizationRequest> request = ArgumentCaptor.forClass(AuthorizationRequest.class);
        InOrder order = inOrder(authorizationPort, projectScopePort, accountRepository, postRepository);
        order.verify(authorizationPort).decide(request.capture());
        order.verify(projectScopePort).belongsToTenant("tenant-a", "project-1");
        order.verify(accountRepository).findPublicationAccount(
                "tenant-a", "actor-1", "project-1", "account-1", 7L);
        order.verify(authorizationPort, times(2)).decide(request.capture());
        order.verify(postRepository).findReadProjection(
                "tenant-a", "actor-1", "project-1", "account-1", 7L, START, END, 25);
        verifyNoMoreInteractions(postRepository);
        assertEquals(actor, request.getAllValues().getFirst().actor());
        assertEquals(List.of("social.read", "social.content.read", "social.artifact.read"),
                request.getAllValues().stream().map(value -> value.action().permissionKey()).toList());
        assertNull(request.getAllValues().getFirst().resource().ownerId());
    }

    @Test
    void detailRepresentsMissingOptionalContentAndPlanWithoutInventingTime() {
        allowProject("actor-1");
        when(accountRepository.findPublicationAccount(
                "tenant-a", "actor-1", "project-1", "account-1", 11L))
                .thenReturn(Optional.of(account("account-1", "actor-1", 11L, "ACTIVE")));
        when(postRepository.findReadProjectionById(
                "tenant-a", "actor-1", "project-1", "account-1", 11L, "post-1"))
                .thenReturn(Optional.of(new SocialPostReadModel(
                        "post-1", "project-1", "account-1", 11L,
                        null, null, "YOUTUBE", null)));

        PublicationPostResponse response = service.get(
                "tenant-a", "project-1", "account-1", 11L, "post-1");

        assertNull(response.contentText());
        assertNull(response.scheduledAt());
        assertEquals(PublicationPostResponse.ContentAvailability.NOT_PROVIDED,
                response.contentAvailability());
        assertEquals(PublicationPostResponse.TimeMeaning.NOT_PROVIDED, response.timeMeaning());
        assertEquals(PublicationPostResponse.TimePrecision.UNKNOWN, response.timePrecision());
        assertEquals(PublicationPostResponse.ContentVersionRelationState.NOT_PROVIDED,
                response.contentVersionRelationState());
        assertEquals(PublicationPostResponse.ArtifactRelationState.NOT_PROVIDED,
                response.artifactRelationState());
    }

    @Test
    void independentlyRedactsContentAndArtifactWithoutRelationExistenceOracle() {
        allowProject("actor-1");
        when(authorizationPort.decide(any())).thenAnswer(invocation -> {
            AuthorizationRequest request = invocation.getArgument(0);
            return "social.read".equals(request.action().permissionKey())
                    ? AuthorizationDecision.allow("RBAC")
                    : AuthorizationDecision.deny("RBAC_DENY", "RBAC");
        });
        when(accountRepository.findPublicationAccount(
                "tenant-a", "actor-1", "project-1", "account-1", 7L))
                .thenReturn(Optional.of(account("account-1", "actor-1", 7L, "ACTIVE")));
        when(postRepository.findReadProjection(
                "tenant-a", "actor-1", "project-1", "account-1", 7L, START, END, 25))
                .thenReturn(List.of(
                        new SocialPostReadModel(
                                "present", "project-1", "account-1", 7L,
                                "secret", "artifact-secret", "YOUTUBE", START),
                        new SocialPostReadModel(
                                "absent", "project-1", "account-1", 7L,
                                null, null, "YOUTUBE", START.plusSeconds(1))));

        PublicationPostListResponse response = service.list(
                "tenant-a", "project-1", "account-1", 7L, START, END, 25);

        for (PublicationPostResponse item : response.items()) {
            assertNull(item.contentText());
            assertNull(item.artifactId());
            assertEquals(PublicationPostResponse.ContentAvailability.RESTRICTED,
                    item.contentAvailability());
            assertEquals(PublicationPostResponse.ContentVersionRelationState.RESTRICTED,
                    item.contentVersionRelationState());
            assertEquals(PublicationPostResponse.ArtifactRelationState.RESTRICTED,
                    item.artifactRelationState());
        }
    }

    @Test
    void mismatchedRepositoryTupleFailsClosedInsteadOfReturningAReceipt() {
        allowProject("actor-1");
        when(accountRepository.findPublicationAccount(
                "tenant-a", "actor-1", "project-1", "account-1", 7L))
                .thenReturn(Optional.of(account("account-1", "actor-1", 7L, "ACTIVE")));
        when(postRepository.findReadProjectionById(
                "tenant-a", "actor-1", "project-1", "account-1", 7L, "post-1"))
                .thenReturn(Optional.of(new SocialPostReadModel(
                        "post-1", "project-1", "account-1", 6L,
                        "secret", null, "YOUTUBE", START)));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.get(
                "tenant-a", "project-1", "account-1", 7L, "post-1"));
        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void forgedHeaderCannotSelectActorBecauseOnlyCanonicalResolverIsUsed() {
        allowProject("real-actor");
        when(accountRepository.findPublicationAccount(
                "tenant-a", "real-actor", "project-1", "account-1", 3L))
                .thenReturn(Optional.of(account("account-1", "real-actor", 3L, "ACTIVE")));
        when(postRepository.findReadProjection(
                "tenant-a", "real-actor", "project-1", "account-1", 3L, START, END, 10))
                .thenReturn(List.of());

        service.list("tenant-a", "project-1", "account-1", 3L, START, END, 10);

        verify(accountRepository, never()).findPublicationAccount(
                "tenant-a", "forged-user", "project-1", "account-1", 3L);
    }

    @Test
    void inactiveStaleOrForeignAccountFailsClosedWithoutPostQuery() {
        allowProject("actor-1");
        when(accountRepository.findPublicationAccount(
                "tenant-a", "actor-1", "project-1", "account-1", 7L))
                .thenReturn(Optional.of(account("account-1", "actor-1", 7L, "DISCONNECTED")));

        assertThrows(ResponseStatusException.class, () -> service.list(
                "tenant-a", "project-1", "account-1", 7L, START, END, 10));

        verifyNoInteractions(postRepository);
    }

    @Test
    void authorizationDenialStopsBeforeProjectAccountOrPostHydration() {
        when(actorResolver.resolveCurrentActor()).thenReturn(Optional.of(
                CanonicalActor.user("actor-1", "tenant-a", Set.of(), "jwt")));
        when(authorizationPort.decide(any())).thenReturn(
                AuthorizationDecision.deny("RBAC_DENY", "RBAC"));

        assertThrows(ResponseStatusException.class, () -> service.list(
                "tenant-a", "project-1", "account-1", 7L, START, END, 10));

        verifyNoInteractions(projectScopePort, accountRepository, postRepository);
    }

    @Test
    void foreignProjectStopsBeforeAccountAndPostLookup() {
        when(actorResolver.resolveCurrentActor()).thenReturn(Optional.of(
                CanonicalActor.user("actor-1", "tenant-a", Set.of(), "jwt")));
        when(authorizationPort.decide(any())).thenReturn(AuthorizationDecision.allow("RBAC"));
        when(projectScopePort.belongsToTenant("tenant-a", "project-1")).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> service.list(
                "tenant-a", "project-1", "account-1", 7L, START, END, 10));

        verifyNoInteractions(accountRepository, postRepository);
    }

    @Test
    void validatesHalfOpenRangeAndLimitBeforeAuthorization() {
        assertThrows(IllegalArgumentException.class, () -> service.list(
                "tenant-a", "project-1", "account-1", 7L, END, START, 10));
        assertThrows(IllegalArgumentException.class, () -> service.list(
                "tenant-a", "project-1", "account-1", 7L, START, END, 201));
        assertThrows(IllegalArgumentException.class, () -> service.list(
                "tenant-a", "project-1", "account-1", 7L, START, END, 0));
        assertThrows(IllegalArgumentException.class, () -> service.list(
                "tenant-a", "project-1", "account-1", 0L, START, END, 10));

        verify(authorizationPort, never()).decide(any());
        verify(postRepository, never()).findReadProjection(
                any(), any(), any(), any(), anyLong(), any(), any(), anyInt());
    }

    private CanonicalActor allowProject(String actorId) {
        CanonicalActor actor = CanonicalActor.user(actorId, "tenant-a", Set.of(), "jwt");
        when(actorResolver.resolveCurrentActor()).thenReturn(Optional.of(actor));
        when(authorizationPort.decide(any())).thenReturn(AuthorizationDecision.allow("RBAC"));
        when(projectScopePort.belongsToTenant("tenant-a", "project-1")).thenReturn(true);
        return actor;
    }

    private static SocialAccountReadModel account(
            String id, String actorId, long bindingVersion, String status) {
        return new SocialAccountReadModel(id, "channel", "YOUTUBE", status, bindingVersion);
    }
}
