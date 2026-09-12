package com.example.platform.social.app;

import com.example.platform.shared.authorization.AuthorizableResourceRef;
import com.example.platform.shared.authorization.AuthorizationAction;
import com.example.platform.shared.authorization.AuthorizationContext;
import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.shared.authorization.AuthorizationDecisionPort;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.shared.authorization.AuthorizationResourceType;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.authorization.CanonicalActorResolver;
import com.example.platform.social.api.dto.PublicationPostListResponse;
import com.example.platform.social.api.dto.PublicationPostResponse;
import com.example.platform.social.infrastructure.persistence.ConnectedPlatformRepository;
import com.example.platform.social.infrastructure.persistence.SocialPostRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Authorization-first application boundary for the single-account publication read slice. */
@Service
public final class SocialPostReadService {

    public static final int MAX_LIMIT = 200;
    private static final AuthorizationAction READ_PROJECT = new AuthorizationAction(
            "social.read", AuthorizationResourceType.PROJECT, "Read project publication records");
    private static final AuthorizationAction READ_CONTENT = new AuthorizationAction(
            "social.content.read", AuthorizationResourceType.PROJECT, "Read publication content fields");
    private static final AuthorizationAction READ_ARTIFACT = new AuthorizationAction(
            "social.artifact.read", AuthorizationResourceType.PROJECT, "Read publication Artifact relationships");

    private final CanonicalActorResolver actorResolver;
    private final AuthorizationDecisionPort authorizationPort;
    private final SocialProjectScopePort projectScopePort;
    private final ConnectedPlatformRepository accountRepository;
    private final SocialPostRepository postRepository;

    public SocialPostReadService(
            CanonicalActorResolver actorResolver,
            AuthorizationDecisionPort authorizationPort,
            SocialProjectScopePort projectScopePort,
            ConnectedPlatformRepository accountRepository,
            SocialPostRepository postRepository) {
        this.actorResolver = Objects.requireNonNull(actorResolver, "actorResolver");
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort");
        this.projectScopePort = Objects.requireNonNull(projectScopePort, "projectScopePort");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.postRepository = Objects.requireNonNull(postRepository, "postRepository");
    }

    public PublicationPostListResponse list(
            String tenantId,
            String projectId,
            String connectedAccountId,
            long bindingVersion,
            Instant start,
            Instant end,
            int limit) {
        validateList(tenantId, projectId, connectedAccountId, bindingVersion, start, end, limit);
        ReadScope scope = authorizeAndResolve(
                tenantId, projectId, connectedAccountId, bindingVersion);
        List<PublicationPostResponse> items = postRepository.findReadProjection(
                        tenantId, scope.actor().actorId(), projectId, connectedAccountId,
                        scope.account().bindingVersion(), start, end, limit)
                .stream().map(row -> toResponse(row, scope, projectId, connectedAccountId)).toList();
        return new PublicationPostListResponse(items, PublicationPostListResponse.Coverage.BOUNDED_PARTIAL);
    }

    public PublicationPostResponse get(
            String tenantId,
            String projectId,
            String connectedAccountId,
            long bindingVersion,
            String postId) {
        requireText(tenantId, "tenantId");
        requireText(projectId, "projectId");
        requireText(connectedAccountId, "connectedAccountId");
        requirePositive(bindingVersion, "bindingVersion");
        requireText(postId, "postId");
        ReadScope scope = authorizeAndResolve(
                tenantId, projectId, connectedAccountId, bindingVersion);
        return postRepository.findReadProjectionById(
                        tenantId, scope.actor().actorId(), projectId, connectedAccountId,
                        scope.account().bindingVersion(), postId)
                .map(row -> toResponse(row, scope, projectId, connectedAccountId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "publication post not found"));
    }

    private ReadScope authorizeAndResolve(
            String tenantId, String projectId, String connectedAccountId, long bindingVersion) {
        CanonicalActor actor = actorResolver.resolveCurrentActor()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authenticated actor required"));
        if (!tenantId.equals(actor.tenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "authenticated actor tenant mismatch");
        }
        AuthorizationDecision decision = authorizationPort.decide(new AuthorizationRequest(
                actor,
                READ_PROJECT,
                new AuthorizableResourceRef(
                        AuthorizationResourceType.PROJECT, projectId, tenantId, projectId, null),
                new AuthorizationContext(
                        "social-publication-read", projectId,
                        Map.of("accountScope", "server-current-exact-binding"))));
        if (!decision.allowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "publication read denied");
        }
        if (!projectScopePort.belongsToTenant(tenantId, projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "project not found");
        }
        SocialAccountReadModel account = accountRepository
                .findPublicationAccount(
                        tenantId, actor.actorId(), projectId, connectedAccountId, bindingVersion)
                .filter(candidate -> "ACTIVE".equals(candidate.status()))
                .filter(candidate -> candidate.bindingVersion() == bindingVersion)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "connected account not found"));
        boolean contentAllowed = decideField(actor, READ_CONTENT, tenantId, projectId, connectedAccountId);
        boolean artifactAllowed = decideField(actor, READ_ARTIFACT, tenantId, projectId, connectedAccountId);
        return new ReadScope(actor, account, contentAllowed, artifactAllowed);
    }

    private boolean decideField(
            CanonicalActor actor,
            AuthorizationAction action,
            String tenantId,
            String projectId,
            String connectedAccountId) {
        return authorizationPort.decide(new AuthorizationRequest(
                actor,
                action,
                new AuthorizableResourceRef(
                        AuthorizationResourceType.PROJECT, projectId, tenantId, projectId, null),
                new AuthorizationContext(
                        "social-publication-field-read", projectId,
                        Map.of("accountScope", connectedAccountId))))
                .allowed();
    }

    private static PublicationPostResponse toResponse(
            SocialPostReadModel row,
            ReadScope scope,
            String projectId,
            String connectedAccountId) {
        if (!projectId.equals(row.projectId())
                || !connectedAccountId.equals(row.connectedAccountId())
                || scope.account().bindingVersion() != row.bindingVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "publication binding changed");
        }
        boolean hasContent = scope.contentAllowed() && row.contentText() != null;
        boolean hasArtifact = scope.artifactAllowed() && row.artifactId() != null;
        boolean hasSchedule = row.scheduledAt() != null;
        return new PublicationPostResponse(
                row.postId(), row.projectId(), row.connectedAccountId(), row.bindingVersion(),
                hasContent ? row.contentText() : null,
                !scope.contentAllowed()
                        ? PublicationPostResponse.ContentAvailability.RESTRICTED
                        : hasContent
                                ? PublicationPostResponse.ContentAvailability.AVAILABLE
                                : PublicationPostResponse.ContentAvailability.NOT_PROVIDED,
                scope.contentAllowed()
                        ? PublicationPostResponse.ContentVersionRelationState.NOT_PROVIDED
                        : PublicationPostResponse.ContentVersionRelationState.RESTRICTED,
                hasArtifact ? row.artifactId() : null,
                !scope.artifactAllowed()
                        ? PublicationPostResponse.ArtifactRelationState.RESTRICTED
                        : hasArtifact
                                ? PublicationPostResponse.ArtifactRelationState.AVAILABLE
                                : PublicationPostResponse.ArtifactRelationState.NOT_PROVIDED,
                row.platformType(), row.scheduledAt(),
                hasSchedule
                        ? PublicationPostResponse.TimeMeaning.PLANNED_PUBLISH_TIME
                        : PublicationPostResponse.TimeMeaning.NOT_PROVIDED,
                hasSchedule
                        ? PublicationPostResponse.TimePrecision.EXACT_INSTANT
                        : PublicationPostResponse.TimePrecision.UNKNOWN,
                PublicationPostResponse.SourceVerification.VERIFIED_LOCAL_RECORD,
                PublicationPostResponse.EndpointAccess.AUTHORIZED_LOCAL_PROJECT_ACCOUNT_READ,
                PublicationPostResponse.GlobalEffectiveAccess.UNKNOWN_FAIL_CLOSED);
    }

    private static void validateList(
            String tenantId, String projectId, String connectedAccountId, long bindingVersion,
            Instant start, Instant end, int limit) {
        requireText(tenantId, "tenantId");
        requireText(projectId, "projectId");
        requireText(connectedAccountId, "connectedAccountId");
        requirePositive(bindingVersion, "bindingVersion");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("start must be before end for [start,end)");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requirePositive(long value, String field) {
        if (value < 1) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private record ReadScope(
            CanonicalActor actor,
            SocialAccountReadModel account,
            boolean contentAllowed,
            boolean artifactAllowed) {}
}
