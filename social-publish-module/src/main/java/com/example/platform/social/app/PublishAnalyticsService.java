package com.example.platform.social.app;

import com.example.platform.social.api.dto.OverviewAnalyticsResponse;
import com.example.platform.social.api.dto.PostAnalyticsResponse;
import com.example.platform.social.domain.ConnectedPlatform;
import com.example.platform.social.domain.PlatformType;
import com.example.platform.social.domain.PostAnalytics;
import com.example.platform.social.domain.PostStatus;
import com.example.platform.social.domain.SocialPost;
import com.example.platform.social.infrastructure.persistence.ConnectedPlatformRepository;
import com.example.platform.social.infrastructure.persistence.PostAnalyticsRepository;
import com.example.platform.social.infrastructure.persistence.SocialPostRepository;
import com.example.platform.social.infrastructure.platform.PlatformAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PublishAnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(PublishAnalyticsService.class);

    private final PostAnalyticsRepository analyticsRepository;
    private final SocialPostRepository postRepository;
    private final ConnectedPlatformRepository platformRepository;
    private final Map<PlatformType, PlatformAdapter> adapters;

    public PublishAnalyticsService(PostAnalyticsRepository analyticsRepository,
                                    SocialPostRepository postRepository,
                                    ConnectedPlatformRepository platformRepository,
                                    List<PlatformAdapter> adapterList) {
        this.analyticsRepository = analyticsRepository;
        this.postRepository = postRepository;
        this.platformRepository = platformRepository;
        this.adapters = adapterList.stream().collect(
                java.util.stream.Collectors.toMap(PlatformAdapter::platform, a -> a));
    }

    public PostAnalyticsResponse getPostAnalytics(String tenantId, String userId, String postId) {
        SocialPost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));

        PlatformType platformType = post.platformType();
        PlatformAdapter adapter = adapters.get(platformType);
        ConnectedPlatform connected = platformRepository.findByTenantUserAndPlatform(
                tenantId, userId, platformType.name()).orElse(null);

        PostAnalytics analytics = adapter.fetchAnalytics(post, connected);
        analytics = analyticsRepository.save(analytics);
        log.info("PublishAnalyticsService: fetched analytics for post={}", postId);
        return toResponse(analytics);
    }

    public OverviewAnalyticsResponse getOverviewAnalytics(String tenantId, String userId) {
        List<SocialPost> allPosts = postRepository.findByTenantAndUser(tenantId, userId, 0, 10000);
        int total = allPosts.size();
        int published = 0, failed = 0, scheduled = 0;
        Map<String, Integer> byPlatform = new HashMap<>();
        Map<String, Integer> byStatus = new HashMap<>();

        for (SocialPost post : allPosts) {
            if (post.status() == PostStatus.PUBLISHED) published++;
            if (post.status() == PostStatus.FAILED) failed++;
            if (post.status() == PostStatus.SCHEDULED) scheduled++;
            byPlatform.merge(post.platformType().name(), 1, Integer::sum);
            byStatus.merge(post.status().name(), 1, Integer::sum);
        }

        log.info("PublishAnalyticsService: overview for user={} total={}", userId, total);
        return new OverviewAnalyticsResponse(total, published, failed, scheduled, byPlatform, byStatus);
    }

    private PostAnalyticsResponse toResponse(PostAnalytics a) {
        return new PostAnalyticsResponse(
                a.id(), a.postId(), a.platformType(),
                a.impressions(), a.reach(), a.likes(),
                a.comments(), a.shares(), a.clicks(),
                a.fetchedAt(), a.createdAt());
    }
}
