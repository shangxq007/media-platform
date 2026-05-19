package com.example.platform.analytics.infrastructure;

import com.example.platform.analytics.domain.UserProfile;

import java.util.List;
import java.util.Optional;

public interface UserProfileRepository {
    UserProfile save(UserProfile profile);
    Optional<UserProfile> findByTenantIdAndUserId(String tenantId, String userId);
    List<UserProfile> findByTenantId(String tenantId, int limit);
    long countByTenantId(String tenantId);
}
