package com.example.platform.social.app;

import com.example.platform.social.domain.PostStatus;
import com.example.platform.social.domain.SocialPost;
import com.example.platform.social.infrastructure.persistence.SocialPostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class PostSchedulerService {
    private static final Logger log = LoggerFactory.getLogger(PostSchedulerService.class);

    private final SocialPostRepository postRepository;
    private final SocialPublishService publishService;

    public PostSchedulerService(SocialPostRepository postRepository, SocialPublishService publishService) {
        this.postRepository = postRepository;
        this.publishService = publishService;
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void processScheduledPosts() {
        Instant now = Instant.now();
        List<SocialPost> duePosts = postRepository.findScheduledBefore(now);
        log.info("PostSchedulerService: found {} scheduled posts due", duePosts.size());
        for (SocialPost post : duePosts) {
            try {
                publishService.publishNow(post.tenantId(), post.userId(), post.id());
            } catch (Exception e) {
                log.error("PostSchedulerService: failed to publish scheduled post={}: {}", post.id(), e.getMessage());
                postRepository.updateStatus(post.id(), PostStatus.FAILED, Instant.now());
            }
        }
    }
}
