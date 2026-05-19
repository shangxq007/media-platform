package com.example.platform.analytics.infrastructure;

import com.example.platform.analytics.domain.UserHabits;

import java.util.List;
import java.util.Optional;

public interface UserHabitsRepository {
    UserHabits save(UserHabits habits);
    Optional<UserHabits> findByTenantIdAndUserId(String tenantId, String userId);
    List<UserHabits> findByTenantId(String tenantId, int limit);
}
