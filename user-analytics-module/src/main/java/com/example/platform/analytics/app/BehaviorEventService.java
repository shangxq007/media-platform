package com.example.platform.analytics.app;

import com.example.platform.analytics.domain.UserBehaviorEvent;
import com.example.platform.analytics.infrastructure.UserBehaviorEventRepository;
import com.example.platform.shared.Ids;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class BehaviorEventService {

    private static final Logger log = LoggerFactory.getLogger(BehaviorEventService.class);

    private final UserBehaviorEventRepository repository;
    private final Counter eventIngestedCounter;

    public BehaviorEventService(UserBehaviorEventRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.eventIngestedCounter = Counter.builder("analytics.events.ingested")
                .description("Number of user behavior events ingested")
                .register(meterRegistry);
    }

    public UserBehaviorEvent ingestEvent(String tenantId, String userId, String eventType,
                                          String action, String resourceType, String resourceId,
                                          Map<String, String> metadata) {
        UserBehaviorEvent event = new UserBehaviorEvent(
                Ids.newId("evt"),
                tenantId,
                userId,
                eventType,
                action,
                resourceType,
                resourceId,
                sanitizeMetadata(metadata),
                Instant.now()
        );

        repository.save(event);
        eventIngestedCounter.increment();

        log.debug("Ingested event {} for tenant {} user {}", event.eventId(), tenantId, userId);
        return event;
    }

    public List<UserBehaviorEvent> findEventsByTenantAndUser(String tenantId, String userId, int limit) {
        return repository.findByTenantIdAndUserId(tenantId, userId, limit);
    }

    public List<UserBehaviorEvent> findEventsByTenant(String tenantId, int limit) {
        return repository.findByTenantId(tenantId, limit);
    }

    public List<UserBehaviorEvent> findEventsByType(String tenantId, String eventType, int limit) {
        return repository.findByTenantIdAndEventType(tenantId, eventType, limit);
    }

    private Map<String, String> sanitizeMetadata(Map<String, String> metadata) {
        if (metadata == null) {
            return Map.of();
        }
        return metadata.entrySet().stream()
                .filter(e -> !isSensitiveKey(e.getKey()))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        return lower.equals("password") || lower.equals("passwd") || lower.equals("pwd")
                || lower.equals("token") || lower.equals("secret") || lower.equals("api_secret")
                || lower.equals("apikey") || lower.equals("api_key")
                || lower.equals("auth") || lower.equals("authorization") || lower.equals("bearer")
                || lower.equals("credential") || lower.equals("credentials")
                || lower.equals("ssn") || lower.equals("social_security")
                || lower.equals("creditcard") || lower.equals("credit_card") || lower.equals("cvv")
                || lower.equals("ip") || lower.equals("ip_address") || lower.equals("user_ip")
                || lower.equals("user-agent") || lower.equals("useragent")
                || lower.equals("cookie") || lower.equals("session_id") || lower.equals("sessionid")
                || lower.equals("x-forwarded-for") || lower.equals("x-real-ip");
    }
}
