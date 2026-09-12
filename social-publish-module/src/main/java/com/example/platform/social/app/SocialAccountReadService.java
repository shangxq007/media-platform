package com.example.platform.social.app;

import com.example.platform.shared.authorization.AuthorizableResourceRef;
import com.example.platform.shared.authorization.AuthorizationAction;
import com.example.platform.shared.authorization.AuthorizationContext;
import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.shared.authorization.AuthorizationResourceType;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.social.api.dto.PublicationAccountResponse;
import com.example.platform.social.infrastructure.persistence.ConnectedPlatformRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Canonical-actor, Project-authorized projection for the existing account query route. */
@Service
public final class SocialAccountReadService {

    private static final AuthorizationAction READ_PROJECT = new AuthorizationAction(
            "social.read", AuthorizationResourceType.PROJECT, "Read publication accounts for project");

    private final CanonicalActorResolver actorResolver;
    private final AuthorizationDecisionPort authorizationPort;
    private final SocialProjectScopePort projectScopePort;
    private final ConnectedPlatformRepository accountRepository;

    public SocialAccountReadService(
            CanonicalActorResolver actorResolver,
            AuthorizationDecisionPort authorizationPort,
            SocialProjectScopePort projectScopePort,
            ConnectedPlatformRepository accountRepository) {
        this.actorResolver = Objects.requireNonNull(actorResolver, "actorResolver");
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort");
        this.projectScopePort = Objects.requireNonNull(projectScopePort, "projectScopePort");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
    }

    public List<PublicationAccountResponse> list(String tenantId, String projectId) {
        requireText(tenantId, "tenantId");
        requireText(projectId, "projectId");
        CanonicalActor actor = actorResolver.resolveCurrentActor()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "authenticated actor required"));
        if (!tenantId.equals(actor.tenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "authenticated actor tenant mismatch");
        }
        AuthorizationDecision decision = authorizationPort.decide(new AuthorizationRequest(
                actor,
                READ_PROJECT,
                new AuthorizableResourceRef(
                        AuthorizationResourceType.PROJECT, projectId, tenantId, projectId, null),
                new AuthorizationContext(
                        "social-publication-account-read", projectId,
                        Map.of("endpointScope", "project-account-identity"))));
        if (!decision.allowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "publication account read denied");
        }
        if (!projectScopePort.belongsToTenant(tenantId, projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "project not found");
        }
        return accountRepository.findPublicationAccounts(tenantId, actor.actorId(), projectId).stream()
                .filter(account -> "ACTIVE".equals(account.status()))
                .map(account -> toResponse(account, projectId))
                .toList();
    }

    private static PublicationAccountResponse toResponse(SocialAccountReadModel account, String projectId) {
        String displayName = account.displayName();
        if (displayName != null && displayName.isBlank()) {
            displayName = null;
        }
        return new PublicationAccountResponse(
                account.accountId(), displayName,
                displayName == null || displayName.isBlank()
                        ? PublicationAccountResponse.DisplayNameAvailability.NOT_PROVIDED
                        : PublicationAccountResponse.DisplayNameAvailability.AVAILABLE,
                account.platformType(), projectId, account.bindingVersion(),
                PublicationAccountResponse.EndpointAccess.AUTHORIZED_LOCAL_PROJECT_ACCOUNT_READ,
                PublicationAccountResponse.GlobalEffectiveAccess.UNKNOWN_FAIL_CLOSED);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
