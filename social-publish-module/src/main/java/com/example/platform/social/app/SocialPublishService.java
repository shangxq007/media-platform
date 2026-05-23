package com.example.platform.social.app;

import com.example.platform.shared.Ids;
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
public class SocialPublishService {
    private static final Logger log = LoggerFactory.getLogger(SocialPublishService.class);

    private final SocialPostRepository postRepository;
    private final ConnectedPlatformRepository platformRepository;
    private final Map<PlatformType, PlatformAdapter> adapters;

    public SocialPublishService(SocialPostRepository postRepository,
                                 ConnectedPlatformRepository platformRepository,
                                 List<PlatformAdapter> adapterList) {
        this.postRepository = postRepository;
        this.platformRepository = platformRepository;
        this.adapters = adapterList.stream().collect(
                java.util.stream.Collectors.toMap(PlatformAdapter::platform, a -> a));
    }

    @Transactional
    public PublishPostResponse createPost(String tenantId, String userId, CreatePostRequest request) {
        Instant now = Instant.now();
        SocialPost post = new SocialPost(
                Ids.newId("pst"), tenantId, userId,
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
        SocialPost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        Instant scheduledAt = Instant.parse(request.scheduledAt());
        SocialPost updated = new SocialPost(
                post.id(), post.tenantId(), post.userId(), post.contentText(), post.mediaUrls(),
                post.platformType(), PostStatus.SCHEDULED, post.platformPostId(), post.platformPostUrl(),
                scheduledAt, null, null, null, null, post.retryCount(),
                post.createdAt(), Instant.now());
        postRepository.save(updated);
        log.info("SocialPublishService: scheduled post={} at {}", postId, scheduledAt);
        return toResponse(updated);
    }

    @Transactional
    public PublishPostResponse publishNow(String tenantId, String userId, String postId) {
        SocialPost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));

        PlatformType platformType = post.platformType();
        PlatformAdapter adapter = adapters.get(platformType);
        if (adapter == null) {
            throw new IllegalStateException("No adapter for platform: " + platformType);
        }

        ConnectedPlatform connected = platformRepository.findByTenantUserAndPlatform(
                tenantId, userId, platformType.name()).orElse(null);

        PublishResult result = adapter.publish(post, connected);
        Instant now = Instant.now();

        SocialPost updated;
        if (result.success()) {
            updated = new SocialPost(
                    post.id(), post.tenantId(), post.userId(), post.contentText(), post.mediaUrls(),
                    post.platformType(), PostStatus.PUBLISHED, result.platformPostId(), result.platformPostUrl(),
                    post.scheduledAt(), now, null, null, null, post.retryCount(),
                    post.createdAt(), now);
        } else {
            updated = new SocialPost(
                    post.id(), post.tenantId(), post.userId(), post.contentText(), post.mediaUrls(),
                    post.platformType(), PostStatus.FAILED, null, null,
                    post.scheduledAt(), null, now, result.errorCode(), result.errorMessage(), post.retryCount() + 1,
                    post.createdAt(), now);
        }
        postRepository.save(updated);
        log.info("SocialPublishService: published post={} success={}", postId, result.success());
        return toResponse(updated);
    }

    @Transactional
    public void cancelScheduled(String tenantId, String userId, String postId) {
        SocialPost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        SocialPost updated = new SocialPost(
                post.id(), post.tenantId(), post.userId(), post.contentText(), post.mediaUrls(),
                post.platformType(), PostStatus.CANCELLED, post.platformPostId(), post.platformPostUrl(),
                null, null, null, null, null, post.retryCount(),
                post.createdAt(), Instant.now());
        postRepository.save(updated);
        log.info("SocialPublishService: cancelled scheduled post={}", postId);
    }

    @Transactional
    public PublishPostResponse retryPost(String tenantId, String userId, String postId) {
        SocialPost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        if (post.status() != PostStatus.FAILED) {
            throw new IllegalStateException("Only failed posts can be retried");
        }
        return publishNow(tenantId, userId, postId);
    }

    @Transactional
    public void deletePost(String tenantId, String userId, String postId) {
        postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        postRepository.deleteById(postId);
        log.info("SocialPublishService: deleted post={}", postId);
    }

    public PublishPostResponse getPost(String tenantId, String userId, String postId) {
        SocialPost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        return toResponse(post);
    }

    public PostHistoryResponse getPosts(String tenantId, String userId, int page, int size) {
        int offset = page * size;
        List<SocialPost> posts = postRepository.findByTenantAndUser(tenantId, userId, offset, size);
        long total = postRepository.countByTenantAndUser(tenantId, userId);
        List<PublishPostResponse> responses = posts.stream().map(this::toResponse).toList();
        return new PostHistoryResponse(responses, total, page, size);
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
