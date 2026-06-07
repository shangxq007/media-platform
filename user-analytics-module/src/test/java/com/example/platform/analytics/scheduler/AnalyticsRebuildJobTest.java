package com.example.platform.analytics.scheduler;

import com.example.platform.analytics.app.UserProfileService;
import com.example.platform.analytics.app.UserSegmentService;
import com.example.platform.analytics.domain.*;
import com.example.platform.analytics.infrastructure.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnalyticsRebuildJobTest {

    private AnalyticsRebuildJob job;
    private InMemoryUserProfileRepository profileRepository;
    private InMemoryUserBehaviorEventRepository eventRepository;
    private InMemoryUserSegmentRepository segmentRepository;

    @BeforeEach
    void setUp() {
        profileRepository = new InMemoryUserProfileRepository();
        eventRepository = new InMemoryUserBehaviorEventRepository();
        segmentRepository = new InMemoryUserSegmentRepository();
        UserProfileService profileService = new UserProfileService(profileRepository, eventRepository);
        UserSegmentService segmentService = new UserSegmentService(segmentRepository, profileRepository, eventRepository);
        job = new AnalyticsRebuildJob(profileService, segmentService, new SimpleMeterRegistry());
    }

    @Test
    void rebuildAllProfilesReturnsZeroWhenEmpty() {
        int count = job.rebuildAllProfiles();
        assertEquals(0, count);
    }

    @Test
    void rebuildAllSegmentsReturnsAtLeastFive() {
        // Insert sample profiles so resolveTargetTenants() finds tenants
        for (int i = 0; i < 3; i++) {
            profileRepository.save(new UserProfile("prof-" + i, "tenant-" + i, "user-" + i,
                    null, java.util.Set.of(), Map.of(), Map.of(), 0, 0,
                    Instant.now(), Instant.now(), Instant.now()));
        }
        int count = job.rebuildAllSegments();
        assertTrue(count >= 5, "Should compute at least 5 default segments, got: " + count);
    }

    @Test
    void rebuildSegmentsIsIdempotent() {
        int first = job.rebuildAllSegments();
        int second = job.rebuildAllSegments();
        assertEquals(first, second);
    }

    @Test
    void rebuildProfilesAfterEventIngestion() {
        eventRepository.save(new UserBehaviorEvent("evt-1", "tenant-1", "user-1",
                "page_view", "view", "dashboard", null, Map.of(), Instant.now()));
        profileRepository.save(new UserProfile("prof-1", "tenant-1", "user-1", null,
                java.util.Set.of(), Map.of(), Map.of(), 0, 0,
                Instant.now(), Instant.now(), Instant.now()));

        int count = job.rebuildAllProfiles();
        assertTrue(count >= 1);
    }

    @Test
    void statusReturnsTimestamps() {
        Map<String, Object> status = job.status();
        assertNotNull(status.get("lastProfileRebuild"));
        assertNotNull(status.get("lastSegmentRebuild"));
    }
}
