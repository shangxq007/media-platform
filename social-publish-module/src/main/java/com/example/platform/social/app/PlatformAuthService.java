package com.example.platform.social.app;

import com.example.platform.social.api.dto.ConnectedPlatformResponse;
import com.example.platform.social.domain.ConnectedPlatform;
import com.example.platform.social.domain.SocialPlatformProviderUnavailableException;
import com.example.platform.social.infrastructure.persistence.ConnectedPlatformRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlatformAuthService {
    private static final Logger log = LoggerFactory.getLogger(PlatformAuthService.class);

    private final ConnectedPlatformRepository platformRepository;

    public PlatformAuthService(ConnectedPlatformRepository platformRepository) {
        this.platformRepository = platformRepository;
    }

    public List<ConnectedPlatformResponse> getConnectedPlatforms(String tenantId, String userId) {
        return platformRepository.findByTenantAndUser(tenantId, userId)
                .stream().map(this::toResponse).toList();
    }

    public ConnectedPlatformResponse connectPlatform(String tenantId, String userId, String platform, String authCode) {
        throw new SocialPlatformProviderUnavailableException(platform);
    }

    public void disconnectPlatform(String tenantId, String userId, String platform) {
        log.info("PlatformAuthService: disconnecting platform={} for user={}", platform, userId);
        ConnectedPlatform existing = platformRepository.findByTenantUserAndPlatform(tenantId, userId, platform)
                .orElseThrow(() -> new IllegalArgumentException("Platform not connected: " + platform));
        platformRepository.deleteById(existing.id());
    }

    private ConnectedPlatformResponse toResponse(ConnectedPlatform p) {
        return new ConnectedPlatformResponse(
                p.id(), p.tenantId(), p.userId(), p.platformType(),
                p.platformUserId(), p.platformUsername(), p.status(),
                p.createdAt(), p.updatedAt());
    }
}
