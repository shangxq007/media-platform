package com.example.platform.analytics.app;

import com.example.platform.analytics.domain.*;
import com.example.platform.analytics.infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UserSegmentServiceTest {

    private UserSegmentService service;
    private InMemoryUserSegmentRepository segmentRepository;
    private InMemoryUserProfileRepository profileRepository;
    private InMemoryUserBehaviorEventRepository eventRepository;

    @BeforeEach
    void setUp() {
        segmentRepository = new InMemoryUserSegmentRepository();
        profileRepository = new InMemoryUserProfileRepository();
        eventRepository = new InMemoryUserBehaviorEventRepository();
        service = new UserSegmentService(segmentRepository, profileRepository, eventRepository);
    }

    @Test
    void computeActiveUsersSegmentFindsRecentUsers() {
        profileRepository.save(new UserProfile("prof-1", "tenant-1", "user-1", null,
                Set.of(), Map.of("dashboard", 5), Map.of("view", 5), 5, 5,
                Instant.now().minusSeconds(3600), Instant.now(), Instant.now()));
        profileRepository.save(new UserProfile("prof-2", "tenant-1", "user-2", null,
                Set.of(), Map.of(), Map.of(), 0, 0,
                Instant.now().minusSeconds(86400 * 60), Instant.now().minusSeconds(86400 * 60), Instant.now()));

        UserSegment segment = service.computeActiveUsersSegment("tenant-1", 30);
        assertEquals(1, segment.userCount());
        assertTrue(segment.userIds().contains("user-1"));
        assertFalse(segment.userIds().contains("user-2"));
    }

    @Test
    void computePowerUsersSegmentFindsHighActivityUsers() {
        profileRepository.save(new UserProfile("prof-1", "tenant-1", "user-1", null,
                Set.of(), Map.of(), Map.of(), 10, 150,
                Instant.now(), Instant.now(), Instant.now()));
        profileRepository.save(new UserProfile("prof-2", "tenant-1", "user-2", null,
                Set.of(), Map.of(), Map.of(), 1, 5,
                Instant.now(), Instant.now(), Instant.now()));

        UserSegment segment = service.computePowerUsersSegment("tenant-1", 100);
        assertEquals(1, segment.userCount());
        assertTrue(segment.userIds().contains("user-1"));
    }

    @Test
    void listSegmentsByTenantReturnsOnlyMatching() {
        service.computePowerUsersSegment("tenant-1", 50);
        service.computePowerUsersSegment("tenant-2", 50);

        List<UserSegment> segments = service.listSegmentsByTenant("tenant-1");
        assertEquals(1, segments.size());
    }
}
