package com.example.platform.analytics.infrastructure;

import com.example.platform.analytics.domain.UserSegment;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class InMemoryUserSegmentRepository implements UserSegmentRepository {

    private final Map<String, UserSegment> segments = new ConcurrentHashMap<>();

    @Override
    public UserSegment save(UserSegment segment) {
        segments.put(segment.segmentId(), segment);
        return segment;
    }

    @Override
    public Optional<UserSegment> findByTenantIdAndSegmentId(String tenantId, String segmentId) {
        UserSegment segment = segments.get(segmentId);
        if (segment != null && segment.tenantId().equals(tenantId)) {
            return Optional.of(segment);
        }
        return Optional.empty();
    }

    @Override
    public List<UserSegment> findByTenantId(String tenantId) {
        return segments.values().stream()
                .filter(s -> s.tenantId().equals(tenantId))
                .collect(Collectors.toList());
    }
}
