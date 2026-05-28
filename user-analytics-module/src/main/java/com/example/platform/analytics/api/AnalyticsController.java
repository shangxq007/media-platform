package com.example.platform.analytics.api;

import com.example.platform.analytics.api.dto.*;
import com.example.platform.analytics.app.*;
import com.example.platform.analytics.domain.*;
import com.example.platform.analytics.scheduler.AnalyticsRebuildJob;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final BehaviorEventService eventService;
    private final UserProfileService profileService;
    private final UserHabitsService habitsService;
    private final UserSegmentService segmentService;
    private final AnalyticsRebuildJob rebuildJob;

    public AnalyticsController(BehaviorEventService eventService,
                               UserProfileService profileService,
                               UserHabitsService habitsService,
                               UserSegmentService segmentService,
                               AnalyticsRebuildJob rebuildJob) {
        this.eventService = eventService;
        this.profileService = profileService;
        this.habitsService = habitsService;
        this.segmentService = segmentService;
        this.rebuildJob = rebuildJob;
    }

    @PostMapping("/events")
    public ResponseEntity<UserBehaviorEventResponse> ingestEvent(
            @RequestBody IngestEventRequest request) {
        String tenantId = com.example.platform.shared.web.TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        UserBehaviorEvent event = eventService.ingestEvent(
                tenantId, request.userId(), request.eventType(),
                request.action(), request.resourceType(), request.resourceId(),
                request.metadata());
        return ResponseEntity.ok(toEventResponse(event));
    }

    @GetMapping("/events")
    public ResponseEntity<List<UserBehaviorEventResponse>> listEvents(
            @RequestParam(defaultValue = "100") int limit) {
        String tenantId = com.example.platform.shared.web.TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        List<UserBehaviorEventResponse> responses = eventService.findEventsByTenant(tenantId, limit)
                .stream().map(this::toEventResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/profiles/{userId}")
    public ResponseEntity<UserProfileResponse> getProfile(
            @PathVariable String userId) {
        String tenantId = com.example.platform.shared.web.TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        UserProfile profile = profileService.aggregateProfile(tenantId, userId);
        return ResponseEntity.ok(toProfileResponse(profile));
    }

    @GetMapping("/profiles")
    public ResponseEntity<List<UserProfileResponse>> listProfiles(
            @RequestParam(defaultValue = "100") int limit) {
        String tenantId = com.example.platform.shared.web.TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        List<UserProfileResponse> responses = profileService.listProfilesByTenant(tenantId, limit)
                .stream().map(this::toProfileResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/habits/{userId}")
    public ResponseEntity<UserHabitsResponse> getHabits(
            @PathVariable String userId) {
        String tenantId = com.example.platform.shared.web.TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        UserHabits habits = habitsService.computeHabits(tenantId, userId);
        return ResponseEntity.ok(toHabitsResponse(habits));
    }

    @GetMapping("/segments/{segmentId}")
    public ResponseEntity<UserSegmentResponse> getSegment(
            @PathVariable String segmentId) {
        String tenantId = com.example.platform.shared.web.TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        return segmentService.findSegment(tenantId, segmentId)
                .map(this::toSegmentResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/segments")
    public ResponseEntity<List<UserSegmentResponse>> listSegments() {
        String tenantId = com.example.platform.shared.web.TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        List<UserSegmentResponse> responses = segmentService.listSegmentsByTenant(tenantId)
                .stream().map(this::toSegmentResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/segments/active")
    public ResponseEntity<UserSegmentResponse> computeActiveSegment(
            @RequestParam(defaultValue = "30") int activeWithinDays) {
        String tenantId = com.example.platform.shared.web.TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        UserSegment segment = segmentService.computeActiveUsersSegment(tenantId, activeWithinDays);
        return ResponseEntity.ok(toSegmentResponse(segment));
    }

    @PostMapping("/internal/rebuild-profiles")
    public ResponseEntity<Map<String, Object>> rebuildProfiles() {
        int count = rebuildJob.rebuildAllProfiles();
        return ResponseEntity.ok(Map.of("status", "completed", "profilesRebuilt", count));
    }

    @PostMapping("/internal/rebuild-segments")
    public ResponseEntity<Map<String, Object>> rebuildSegments() {
        int count = rebuildJob.rebuildAllSegments();
        return ResponseEntity.ok(Map.of("status", "completed", "segmentsRebuilt", count));
    }

    @GetMapping("/internal/scheduler-status")
    public ResponseEntity<Map<String, Object>> schedulerStatus() {
        return ResponseEntity.ok(rebuildJob.status());
    }

    @PostMapping("/segments/power-users")
    public ResponseEntity<UserSegmentResponse> computePowerUsersSegment(
            @RequestParam(defaultValue = "100") int minActions) {
        String tenantId = com.example.platform.shared.web.TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        UserSegment segment = segmentService.computePowerUsersSegment(tenantId, minActions);
        return ResponseEntity.ok(toSegmentResponse(segment));
    }

    private UserBehaviorEventResponse toEventResponse(UserBehaviorEvent event) {
        return new UserBehaviorEventResponse(
                event.eventId(), event.tenantId(), event.userId(),
                event.eventType(), event.action(), event.resourceType(),
                event.resourceId(), event.metadata(), event.occurredAt());
    }

    private UserProfileResponse toProfileResponse(UserProfile profile) {
        return new UserProfileResponse(
                profile.profileId(), profile.tenantId(), profile.userId(),
                profile.preferredLanguages(), profile.featureUsageCounts(),
                profile.actionCounts(), profile.totalSessions(), profile.totalActions(),
                profile.firstSeenAt(), profile.lastActiveAt(), profile.updatedAt());
    }

    private UserHabitsResponse toHabitsResponse(UserHabits habits) {
        return new UserHabitsResponse(
                habits.tenantId(), habits.userId(), habits.dailyActivityBuckets(),
                habits.weeklyActivityPattern(), habits.mostUsedFeatures(),
                habits.mostUsedActions(), habits.averageSessionDepth(),
                habits.peakActivityHour(), habits.peakActivityDay(),
                habits.retentionDays(), habits.computedAt());
    }

    private UserSegmentResponse toSegmentResponse(UserSegment segment) {
        return new UserSegmentResponse(
                segment.segmentId(), segment.tenantId(), segment.name(),
                segment.description(), segment.criteria(), segment.userIds(),
                segment.userCount(), segment.computedAt());
    }
}
