package com.example.platform.analytics.infrastructure;

import com.example.platform.analytics.domain.UserSegment;

import java.util.List;
import java.util.Optional;

public interface UserSegmentRepository {
    UserSegment save(UserSegment segment);
    Optional<UserSegment> findByTenantIdAndSegmentId(String tenantId, String segmentId);
    List<UserSegment> findByTenantId(String tenantId);
}
