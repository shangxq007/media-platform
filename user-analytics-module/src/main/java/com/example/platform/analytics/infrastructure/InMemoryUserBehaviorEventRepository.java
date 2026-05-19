package com.example.platform.analytics.infrastructure;

import com.example.platform.analytics.domain.UserBehaviorEvent;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class InMemoryUserBehaviorEventRepository implements UserBehaviorEventRepository {

    private final Map<String, UserBehaviorEvent> events = new ConcurrentHashMap<>();

    @Override
    public UserBehaviorEvent save(UserBehaviorEvent event) {
        events.put(event.eventId(), event);
        return event;
    }

    @Override
    public Optional<UserBehaviorEvent> findByEventId(String eventId) {
        return Optional.ofNullable(events.get(eventId));
    }

    @Override
    public List<UserBehaviorEvent> findByTenantId(String tenantId, int limit) {
        return events.values().stream()
                .filter(e -> e.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(UserBehaviorEvent::occurredAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserBehaviorEvent> findByTenantIdAndUserId(String tenantId, String userId, int limit) {
        return events.values().stream()
                .filter(e -> e.tenantId().equals(tenantId) && e.userId().equals(userId))
                .sorted(Comparator.comparing(UserBehaviorEvent::occurredAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserBehaviorEvent> findByTenantIdAndEventType(String tenantId, String eventType, int limit) {
        return events.values().stream()
                .filter(e -> e.tenantId().equals(tenantId) && e.eventType().equals(eventType))
                .sorted(Comparator.comparing(UserBehaviorEvent::occurredAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public long countByTenantId(String tenantId) {
        return events.values().stream()
                .filter(e -> e.tenantId().equals(tenantId))
                .count();
    }
}
