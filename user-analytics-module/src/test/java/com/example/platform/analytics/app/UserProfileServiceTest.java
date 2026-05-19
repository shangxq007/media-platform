package com.example.platform.analytics.app;

import com.example.platform.analytics.domain.*;
import com.example.platform.analytics.infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UserProfileServiceTest {

    private UserProfileService service;
    private InMemoryUserProfileRepository profileRepository;
    private InMemoryUserBehaviorEventRepository eventRepository;

    @BeforeEach
    void setUp() {
        profileRepository = new InMemoryUserProfileRepository();
        eventRepository = new InMemoryUserBehaviorEventRepository();
        service = new UserProfileService(profileRepository, eventRepository);
    }

    @Test
    void getOrCreateProfileCreatesDefaultWhenMissing() {
        UserProfile profile = service.getOrCreateProfile("tenant-1", "user-1");
        assertNotNull(profile);
        assertEquals("tenant-1", profile.tenantId());
        assertEquals("user-1", profile.userId());
    }

    @Test
    void aggregateProfileCountsFeatureUsage() {
        eventRepository.save(new UserBehaviorEvent("evt-1", "tenant-1", "user-1", "view", "view", "dashboard", null, Map.of(), Instant.now()));
        eventRepository.save(new UserBehaviorEvent("evt-2", "tenant-1", "user-1", "view", "click", "dashboard", null, Map.of(), Instant.now()));
        eventRepository.save(new UserBehaviorEvent("evt-3", "tenant-1", "user-1", "action", "submit", "render-job", null, Map.of(), Instant.now()));

        UserProfile profile = service.aggregateProfile("tenant-1", "user-1");
        assertNotNull(profile);
        assertEquals(2, profile.featureUsageCounts().get("dashboard"));
        assertEquals(1, profile.featureUsageCounts().get("render-job"));
    }

    @Test
    void aggregateProfileExtractsLanguages() {
        eventRepository.save(new UserBehaviorEvent("evt-1", "tenant-1", "user-1", "view", "view", "page", null, Map.of("language", "en"), Instant.now()));
        eventRepository.save(new UserBehaviorEvent("evt-2", "tenant-1", "user-1", "view", "view", "page", null, Map.of("language", "fr"), Instant.now()));

        UserProfile profile = service.aggregateProfile("tenant-1", "user-1");
        assertTrue(profile.preferredLanguages().contains("en"));
        assertTrue(profile.preferredLanguages().contains("fr"));
    }

    @Test
    void listProfilesByTenantReturnsOnlyMatching() {
        service.getOrCreateProfile("tenant-1", "user-1");
        service.getOrCreateProfile("tenant-1", "user-2");
        service.getOrCreateProfile("tenant-2", "user-3");

        List<UserProfile> profiles = service.listProfilesByTenant("tenant-1", 10);
        assertEquals(2, profiles.size());
    }
}
