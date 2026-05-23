package com.example.platform.analytics.infrastructure;

import com.example.platform.analytics.domain.UserHabits;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
@ConditionalOnMissingBean(JdbcTemplate.class)
public class InMemoryUserHabitsRepository implements UserHabitsRepository {

    private final Map<String, UserHabits> habits = new ConcurrentHashMap<>();

    @Override
    public UserHabits save(UserHabits h) {
        String key = h.tenantId() + ":" + h.userId();
        habits.put(key, h);
        return h;
    }

    @Override
    public Optional<UserHabits> findByTenantIdAndUserId(String tenantId, String userId) {
        String key = tenantId + ":" + userId;
        return Optional.ofNullable(habits.get(key));
    }

    @Override
    public List<UserHabits> findByTenantId(String tenantId, int limit) {
        return habits.values().stream()
                .filter(h -> h.tenantId().equals(tenantId))
                .limit(limit)
                .collect(Collectors.toList());
    }
}
