package com.example.platform.analytics.infrastructure;

import com.example.platform.analytics.domain.UserBehaviorEvent;

import java.util.List;
import java.util.Optional;

public interface UserBehaviorEventRepository {
    UserBehaviorEvent save(UserBehaviorEvent event);
    Optional<UserBehaviorEvent> findByEventId(String eventId);
    List<UserBehaviorEvent> findByTenantId(String tenantId, int limit);
    List<UserBehaviorEvent> findByTenantIdAndUserId(String tenantId, String userId, int limit);
    List<UserBehaviorEvent> findByTenantIdAndEventType(String tenantId, String eventType, int limit);
    long countByTenantId(String tenantId);
}
