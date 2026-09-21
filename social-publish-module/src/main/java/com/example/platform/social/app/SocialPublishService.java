package com.example.platform.social.app;

import com.example.platform.social.api.dto.*;
import com.example.platform.social.domain.*;
import com.example.platform.social.infrastructure.persistence.ConnectedPlatformRepository;
import com.example.platform.social.infrastructure.persistence.SocialPostRepository;
import com.example.platform.social.infrastructure.platform.PlatformAdapter;
import com.example.platform.social.infrastructure.platform.PublishResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "app.social-publish", name = "enabled", havingValue = "true")
public class SocialPublishService {
    private static final Logger log = LoggerFactory.getLogger(SocialPublishService.class);

    private final SocialPostRepository postRepository;
    private final ConnectedPlatformRepository platformRepository;
    private final Map<PlatformType, PlatformAdapter> adapters;
    private final org.springframework.transaction.support.TransactionTemplate transactions;
    private final SocialProjectScopePort projects;
    private final com.example.platform.identity.api.authorization.AuthorizationDecisionPort authorization;


    public SocialPublishService(SocialPostRepository postRepository,
                                 ConnectedPlatformRepository platformRepository,
                                 List<PlatformAdapter> adapterList,
                                 org.springframework.transaction.PlatformTransactionManager transactionManager,
                                 SocialProjectScopePort projects,
                                 com.example.platform.identity.api.authorization.AuthorizationDecisionPort authorization) {
        if (adapterList.size() != 1) {
            throw new IllegalStateException("app.social-publish.enabled requires exactly one PlatformAdapter");
        }
        this.transactions = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.projects = projects;
        this.authorization = authorization;
        this.postRepository = postRepository;
        this.platformRepository = platformRepository;
        this.adapters = adapterList.stream().collect(
                java.util.stream.Collectors.toMap(PlatformAdapter::platform, a -> a));
    }

    @Transactional
    public PublishPostResponse createPost(String tenantId, String userId, CreatePostRequest request) {
        Instant now = Instant.now();
        SocialPost post = new SocialPost(
                ("pst_" + java.util.UUID.randomUUID().toString().replace("-", "")), tenantId, userId,
                null, null, null, null,
                request.contentText(), request.mediaUrls() != null ? request.mediaUrls() : List.of(),
                PlatformType.valueOf(request.platformType()),
                PostStatus.DRAFT, null, null, null, null, null,
                null, null, 0, now, now);
        post = postRepository.save(post);
        log.info("SocialPublishService: created post={} for user={}", post.id(), userId);
        return toResponse(post);
    }

    @Transactional
    public PublishPostResponse schedulePost(String tenantId, String userId, String postId, SchedulePostRequest request) {
        requireChanged(postRepository.schedule(tenantId, userId, postId, Instant.parse(request.scheduledAt()), Instant.now()));
        return toResponse(postRepository.findById(postId).orElseThrow());
    }

    /** External effects cannot participate in a caller's rollback. Reject before claiming or dispatching. */
    public PublishPostResponse publishNow(String tenantId, String userId, String postId) {
        noEnclosingTransaction();
        SocialPost post = ownedPost(tenantId, userId, postId);
        return publish(tenantId, userId, postId, post.status());
    }

    public void publishScheduled(String tenantId, String userId, String postId) {
        noEnclosingTransaction();
        publish(tenantId, userId, postId, PostStatus.SCHEDULED);
    }

    public PublishPostResponse retryPost(String tenantId, String userId, String postId) {
        noEnclosingTransaction();
        return publish(tenantId, userId, postId, PostStatus.FAILED);
    }

    private PublishPostResponse publish(String tenant, String actor, String id, PostStatus expected) {
        var attempt = transactions.execute(tx -> postRepository.claim(tenant, actor, id,
                java.util.UUID.randomUUID().toString(), expected, Instant.now()).orElseThrow(
                () -> new IllegalStateException("Publication is not eligible or already owned")));
        var post = attempt.post();
        PlatformAdapter adapter;
        ConnectedPlatform account;
        try {
            adapter = adapters.get(post.platformType());
            if (adapter == null) throw new IllegalStateException("No adapter for platform");
            account = validateBinding(post, false);
            if (account.credentialExpiresAt() != null && !account.credentialExpiresAt().isAfter(Instant.now()))
                throw new IllegalStateException("Provider credentials are expired");
            if (!adapter.validateCredentials(account)) throw new IllegalStateException("Invalid provider credentials");
            var credentialSnapshot = account.credentialSnapshot();
            // Recheck the exact captured binding and canonical permission at the committed dispatch boundary.
            transactions.executeWithoutResult(tx -> {
                var finalAccount = validateBinding(post, true);
                if (!account.equals(finalAccount) || !credentialSnapshot.equals(finalAccount.credentialSnapshot()))
                    throw new IllegalStateException("Account credentials changed before dispatch");
                if (finalAccount.credentialExpiresAt() != null && !finalAccount.credentialExpiresAt().isAfter(Instant.now()))
                    throw new IllegalStateException("Provider credentials expired before dispatch");
                requireChanged(postRepository.markDispatched(attempt, credentialSnapshot, Instant.now()));
            });
        } catch (RuntimeException failure) {
            try { transactions.executeWithoutResult(tx -> postRepository.failBeforeDispatch(attempt, Instant.now())); }
            catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
        try {
            PublishResult result = adapter.publish(post, account);
            if (result == null || !result.success() || result.platformPostId() == null || result.platformPostId().isBlank())
                throw new IllegalStateException("Provider did not confirm publication; outcome unresolved");
            transactions.executeWithoutResult(tx -> requireChanged(postRepository.complete(
                    attempt, result.platformPostId(), result.platformPostUrl(), Instant.now())));
        } catch (RuntimeException failure) {
            // Even a negative response has no contractual guarantee of zero external effects.
            // If persistence is unavailable, the committed dispatch marker still blocks redispatch.
            try { transactions.executeWithoutResult(tx -> postRepository.unresolved(attempt, Instant.now())); }
            catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
        return toResponse(postRepository.findById(id).orElseThrow());
    }

    private ConnectedPlatform validateBinding(SocialPost post, boolean lock) {
        if (post.projectId() == null || post.connectedPlatformId() == null || post.connectedPlatformBindingVersion() == null)
            throw new IllegalStateException("Publication requires an explicit account and Project binding");
        if (!projects.belongsToTenant(post.tenantId(), post.projectId()))
            throw new IllegalStateException("Publication Project unavailable");
        // Persisted owner reference is a background delegation, not a fabricated request principal.
        // Identity revalidates current membership, Workspace, Project and RBAC on every dispatch.
        authorization.requireAuthorized(new com.example.platform.shared.authorization.AuthorizationRequest(
                com.example.platform.shared.authorization.CanonicalActor.user(post.userId(), post.tenantId(),
                        java.util.Set.of(), "social-persisted-owner"),
                new com.example.platform.shared.authorization.AuthorizationAction("social.publish",
                        com.example.platform.shared.authorization.AuthorizationResourceType.PROJECT, "Publish bound post"),
                new com.example.platform.shared.authorization.AuthorizableResourceRef(
                        com.example.platform.shared.authorization.AuthorizationResourceType.PROJECT,
                        post.projectId(), post.tenantId(), post.projectId(), null),
                new com.example.platform.shared.authorization.AuthorizationContext("social-publication", null, Map.of())));
        var account = (lock ? platformRepository.lockById(post.connectedPlatformId())
                : platformRepository.findById(post.connectedPlatformId())).orElseThrow(
                () -> new IllegalStateException("Bound account unavailable"));
        if (!post.tenantId().equals(account.tenantId()) || !post.userId().equals(account.userId())
                || !post.platformType().name().equals(account.platformType())
                || post.connectedPlatformBindingVersion().longValue() != account.bindingVersion()
                || !"ACTIVE".equals(account.status())) throw new IllegalStateException("Invalid account binding");
        return account;
    }

    private SocialPost ownedPost(String tenant, String actor, String id) {
        com.example.platform.shared.web.TenantGuard.assertSameTenant(tenant);
        return postRepository.findById(id).filter(p -> tenant.equals(p.tenantId()) && actor.equals(p.userId()))
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
    }

    private static void noEnclosingTransaction() {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Publication cannot run inside an enclosing transaction");
    }

    private static void requireChanged(boolean changed) {
        if (!changed) throw new IllegalStateException("Publication transition rejected: stale or ineligible state");
    }

    @Transactional
    public void cancelScheduled(String tenantId, String userId, String postId) {
        requireChanged(postRepository.cancel(tenantId, userId, postId, Instant.now()));
    }

    @Transactional
    public void deletePost(String tenantId, String userId, String postId) {
        requireChanged(postRepository.delete(tenantId, userId, postId));
    }

    public List<PublishPostResponse> getDrafts(String tenantId, String userId) {
        return postRepository.findByStatus(tenantId, userId, PostStatus.DRAFT)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public PublishPostResponse saveDraft(String tenantId, String userId, CreatePostRequest request) {
        return createPost(tenantId, userId, request);
    }

    private PublishPostResponse toResponse(SocialPost post) {
        return new PublishPostResponse(
                post.id(), post.tenantId(), post.userId(), post.contentText(), post.mediaUrls(),
                post.platformType().name(), post.status().name(), post.platformPostId(), post.platformPostUrl(),
                post.scheduledAt(), post.publishedAt(), post.failedAt(),
                post.errorCode(), post.errorMessage(), post.retryCount(),
                post.createdAt(), post.updatedAt());
    }
}
