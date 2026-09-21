package com.example.platform.social.app;

import com.example.platform.social.domain.SocialPost;
import com.example.platform.social.infrastructure.persistence.SocialPostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "app.social-publish", name = {"enabled", "scheduler.enabled"}, havingValue = "true")
public class PostSchedulerService {
    private static final Logger log = LoggerFactory.getLogger(PostSchedulerService.class);

    private final SocialPostRepository postRepository;
    private final SocialPublishService publishService;

    public PostSchedulerService(SocialPostRepository postRepository, SocialPublishService publishService) {
        this.postRepository = postRepository;
        this.publishService = publishService;
    }

    @Scheduled(fixedDelay = 60000)
    public void processScheduledPosts() {
        Instant now = Instant.now();
        List<SocialPost> duePosts = postRepository.findScheduledBefore(now);
        log.info("PostSchedulerService: found {} scheduled posts due", duePosts.size());
        for (SocialPost post : duePosts) {
            String previousTenant = com.example.platform.shared.web.TenantContext.get();
            try {
                com.example.platform.shared.web.TenantContext.set(post.tenantId());
                publishService.publishScheduled(post.tenantId(), post.userId(), post.id());
            } catch (Exception e) {
                log.error("PostSchedulerService: failed to publish scheduled post={}", post.id(), e);
            } finally {
                if (previousTenant == null) com.example.platform.shared.web.TenantContext.clear();
                else com.example.platform.shared.web.TenantContext.set(previousTenant);
            }
        }
    }
}
