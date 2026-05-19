package com.example.platform.analytics.app;

import com.example.platform.analytics.domain.*;
import com.example.platform.analytics.infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UserHabitsServiceTest {

    private UserHabitsService service;
    private InMemoryUserHabitsRepository habitsRepository;
    private InMemoryUserBehaviorEventRepository eventRepository;

    @BeforeEach
    void setUp() {
        habitsRepository = new InMemoryUserHabitsRepository();
        eventRepository = new InMemoryUserBehaviorEventRepository();
        service = new UserHabitsService(habitsRepository, eventRepository);
    }

    @Test
    void computeHabitsReturnsDefaultForNoEvents() {
        UserHabits habits = service.computeHabits("tenant-1", "user-1");
        assertNotNull(habits);
        assertEquals("tenant-1", habits.tenantId());
        assertEquals("user-1", habits.userId());
        assertEquals(0.0, habits.averageSessionDepth());
    }

    @Test
    void computeHabitsIdentifiesPeakActivity() {
        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            eventRepository.save(new UserBehaviorEvent("evt-" + i, "tenant-1", "user-1",
                    "view", "view", "dashboard", null, Map.of(), now.plusSeconds(i * 60)));
        }

        UserHabits habits = service.computeHabits("tenant-1", "user-1");
        assertNotNull(habits.peakActivityHour());
        assertFalse(habits.mostUsedFeatures().isEmpty());
        assertTrue(habits.mostUsedFeatures().contains("dashboard"));
    }

    @Test
    void computeHabitsTracksRetentionDays() {
        Instant day1 = Instant.parse("2025-01-01T10:00:00Z");
        Instant day3 = Instant.parse("2025-01-03T10:00:00Z");
        eventRepository.save(new UserBehaviorEvent("evt-1", "tenant-1", "user-1", "view", "view", "page", null, Map.of(), day1));
        eventRepository.save(new UserBehaviorEvent("evt-2", "tenant-1", "user-1", "view", "view", "page", null, Map.of(), day3));

        UserHabits habits = service.computeHabits("tenant-1", "user-1");
        assertTrue(habits.retentionDays() >= 3);
    }
}
