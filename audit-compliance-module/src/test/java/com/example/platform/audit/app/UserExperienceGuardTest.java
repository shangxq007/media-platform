package com.example.platform.audit.app;

import com.example.platform.audit.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserExperienceGuardTest {

    private UserExperienceGuard guard;
    private UsageAnomalyDetectionService anomalyService;

    @BeforeEach
    void setUp() {
        anomalyService = mock(UsageAnomalyDetectionService.class);
        guard = new UserExperienceGuard(anomalyService);
    }

    @Test
    void shouldAllowWhenNoAnomalies() {
        UserExperienceGuard.ExperienceGuardResult result = guard.guard(
                "tenant-1", "user-1", "FREE", List.of(), List.of());
        assertTrue(result.allowNewJobs());
        assertFalse(result.hasAnomaly());
        assertEquals("ALLOW", result.action());
    }

    @Test
    void shouldWarnForAnomalies() {
        UsageMitigationAction warnAction = new UsageMitigationAction(
                "a1", "tenant-1", "user-1", "render_burst",
                UsageMitigationAction.ACTION_WARN, "High frequency", null, false,
                java.time.OffsetDateTime.now());
        UserExperienceGuard.ExperienceGuardResult result = guard.guard(
                "tenant-1", "user-1", "FREE",
                List.of("render_burst"), List.of(warnAction));
        assertTrue(result.allowNewJobs());
        assertTrue(result.hasAnomaly());
        assertEquals("WARN", result.action());
    }

    @Test
    void shouldDegradeInsteadOfBlockForRegularUsers() {
        UsageMitigationAction blockAction = new UsageMitigationAction(
                "a1", "tenant-1", "user-1", "render_burst",
                UsageMitigationAction.ACTION_HARD_BLOCK, "Too many jobs", "default_720p", false,
                java.time.OffsetDateTime.now());
        UserExperienceGuard.ExperienceGuardResult result = guard.guard(
                "tenant-1", "user-1", "FREE",
                List.of("render_burst"), List.of(blockAction));
        assertTrue(result.allowNewJobs());
        assertEquals("DEGRADE", result.action());
        assertNotNull(result.recommendedPreset());
    }

    @Test
    void shouldRequireReviewForHighValueUsers() {
        UsageMitigationAction blockAction = new UsageMitigationAction(
                "a1", "tenant-1", "user-1", "render_burst",
                UsageMitigationAction.ACTION_HARD_BLOCK, "Too many jobs", "default_720p", false,
                java.time.OffsetDateTime.now());
        UserExperienceGuard.ExperienceGuardResult result = guard.guard(
                "tenant-1", "user-1", "ENTERPRISE",
                List.of("render_burst"), List.of(blockAction));
        assertTrue(result.allowNewJobs());
        assertEquals("REVIEW", result.action());
        assertTrue(result.userFriendlyMessage().contains("review"));
    }

    @Test
    void shouldNeverCancelRunningJobs() {
        // All actions should allow new jobs (never cancel in-progress)
        for (String actionType : List.of(UsageMitigationAction.ACTION_WARN,
                UsageMitigationAction.ACTION_SOFT_LIMIT, UsageMitigationAction.ACTION_DEGRADE)) {
            UsageMitigationAction action = new UsageMitigationAction(
                    "a1", "tenant-1", "user-1", "test", actionType, "test", null, false,
                    java.time.OffsetDateTime.now());
            UserExperienceGuard.ExperienceGuardResult result = guard.guard(
                    "tenant-1", "user-1", "FREE", List.of("test"), List.of(action));
            assertTrue(result.allowNewJobs(), "Action " + actionType + " should allow new jobs");
        }
    }
}
