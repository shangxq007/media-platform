package com.example.platform.analytics.infrastructure;

import com.example.platform.analytics.domain.UserProfile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
@ConditionalOnMissingBean(JdbcTemplate.class)
public class InMemoryUserProfileRepository implements UserProfileRepository {

    private final Map<String, UserProfile> profiles = new ConcurrentHashMap<>();

    @Override
    public UserProfile save(UserProfile profile) {
        String key = profile.tenantId() + ":" + profile.userId();
        profiles.put(key, profile);
        return profile;
    }

    @Override
    public Optional<UserProfile> findByTenantIdAndUserId(String tenantId, String userId) {
        String key = tenantId + ":" + userId;
        return Optional.ofNullable(profiles.get(key));
    }

    @Override
    public List<UserProfile> findByTenantId(String tenantId, int limit) {
        return profiles.values().stream()
                .filter(p -> p.tenantId().equals(tenantId))
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public long countByTenantId(String tenantId) {
        return profiles.values().stream()
                .filter(p -> p.tenantId().equals(tenantId))
                .count();
    }
}
