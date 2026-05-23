package com.example.platform.social.app;

import com.example.platform.shared.Ids;
import com.example.platform.social.api.dto.ConnectedPlatformResponse;
import com.example.platform.social.domain.ConnectedPlatform;
import com.example.platform.social.infrastructure.persistence.ConnectedPlatformRepository;
import com.example.platform.social.infrastructure.platform.PlatformAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class PlatformAuthService {
    private static final Logger log = LoggerFactory.getLogger(PlatformAuthService.class);

    private final ConnectedPlatformRepository platformRepository;
    private final Map<String, PlatformAdapter> adapterByPlatform;

    public PlatformAuthService(ConnectedPlatformRepository platformRepository, List<PlatformAdapter> adapters) {
        this.platformRepository = platformRepository;
        this.adapterByPlatform = adapters.stream().collect(
                java.util.stream.Collectors.toMap(a -> a.platform().name(), a -> a));
    }

    public List<ConnectedPlatformResponse> getConnectedPlatforms(String tenantId, String userId) {
        return platformRepository.findByTenantAndUser(tenantId, userId)
                .stream().map(this::toResponse).toList();
    }

    public ConnectedPlatformResponse connectPlatform(String tenantId, String userId, String platform, String authCode) {
        log.info("PlatformAuthService: connecting platform={} for user={}", platform, userId);
        PlatformAdapter adapter = adapterByPlatform.get(platform);
        if (adapter == null) {
            throw new IllegalArgumentException("Unsupported platform: " + platform);
        }

        Instant now = Instant.now();
        ConnectedPlatform connected = new ConnectedPlatform(
                Ids.newId("cn"), tenantId, userId, platform,
                "stub_user_" + platform.toLowerCase(), "@stub_" + platform.toLowerCase(),
                "ACTIVE", now, now);
        connected = platformRepository.save(connected);
        log.info("PlatformAuthService: connected platform={} as id={}", platform, connected.id());
        return toResponse(connected);
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
