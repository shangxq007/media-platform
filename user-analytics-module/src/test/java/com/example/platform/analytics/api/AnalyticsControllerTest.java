package com.example.platform.analytics.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.analytics.api.dto.*;
import com.example.platform.analytics.app.*;
import com.example.platform.analytics.domain.*;
import com.example.platform.analytics.scheduler.AnalyticsRebuildJob;
import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    @Mock
    private BehaviorEventService eventService;

    @Mock
    private UserProfileService profileService;

    @Mock
    private UserHabitsService habitsService;

    @Mock
    private UserSegmentService segmentService;

    @Mock
    private AnalyticsRebuildJob rebuildJob;

    private AnalyticsController controller;

    @BeforeEach
    void setUp() {
        controller = new AnalyticsController(eventService, profileService, habitsService, segmentService, rebuildJob);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static UserBehaviorEvent sampleEvent() {
        return new UserBehaviorEvent("evt-1", "tenant-a", "user-1",
                "page_view", "view", "project", "proj-1", Map.of(), Instant.now());
    }

    @Test
    void ingestEventUsesTenantContext() {
        TenantContext.set("tenant-a");
        when(eventService.ingestEvent("tenant-a", "user-1", "page_view", "view", "project", "proj-1", Map.of()))
                .thenReturn(sampleEvent());

        IngestEventRequest request = new IngestEventRequest("user-1", "page_view", "view", "project", "proj-1", Map.of());
        var response = controller.ingestEvent(request);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertEquals("tenant-a", response.getBody().tenantId());
        verify(eventService).ingestEvent("tenant-a", "user-1", "page_view", "view", "project", "proj-1", Map.of());
    }

    @Test
    void ingestEventRejectsWithoutTenantContext() {
        TenantContext.clear();
        IngestEventRequest request = new IngestEventRequest("user-1", "page_view", null, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> controller.ingestEvent(request));
    }

    @Test
    void listEventsUsesTenantContext() {
        TenantContext.set("tenant-a");
        when(eventService.findEventsByTenant("tenant-a", 100))
                .thenReturn(List.of());

        controller.listEvents(100);

        verify(eventService).findEventsByTenant("tenant-a", 100);
        verify(eventService, never()).findEventsByTenant("tenant-b", 100);
    }

    @Test
    void getProfileUsesTenantContext() {
        TenantContext.set("tenant-a");
        UserProfile profile = new UserProfile("prof-1", "tenant-a", "user-1",
                "User One", Set.of(), Map.of(), Map.of(), 0, 0, Instant.now(), Instant.now(), Instant.now());
        when(profileService.aggregateProfile("tenant-a", "user-1")).thenReturn(profile);

        var response = controller.getProfile("user-1");

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertEquals("tenant-a", response.getBody().tenantId());
    }

    @Test
    void getProfileRejectsWithoutTenantContext() {
        TenantContext.clear();
        assertThrows(IllegalArgumentException.class,
                () -> controller.getProfile("user-1"));
    }

    @Test
    void listProfilesUsesTenantContext() {
        TenantContext.set("tenant-a");
        when(profileService.listProfilesByTenant("tenant-a", 100))
                .thenReturn(List.of());

        controller.listProfiles(100);

        verify(profileService).listProfilesByTenant("tenant-a", 100);
    }

    @Test
    void getHabitsUsesTenantContext() {
        TenantContext.set("tenant-a");
        UserHabits habits = new UserHabits("tenant-a", "user-1",
                Map.of(), Map.of(), List.of(), List.of(), 0.0, "0", "0", 0, Instant.now());
        when(habitsService.computeHabits("tenant-a", "user-1")).thenReturn(habits);

        var response = controller.getHabits("user-1");

        assertNotNull(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("tenant-a", response.getBody().tenantId());
    }

    @Test
    void getSegmentUsesTenantContext() {
        TenantContext.set("tenant-a");
        UserSegment segment = new UserSegment("seg-1", "tenant-a", "Active Users",
                "desc", Map.of(), List.of(), 0, Instant.now());
        when(segmentService.findSegment("tenant-a", "seg-1"))
                .thenReturn(java.util.Optional.of(segment));

        var response = controller.getSegment("seg-1");

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertEquals("tenant-a", response.getBody().tenantId());
    }

    @Test
    void listSegmentsUsesTenantContext() {
        TenantContext.set("tenant-a");
        when(segmentService.listSegmentsByTenant("tenant-a"))
                .thenReturn(List.of());

        controller.listSegments();

        verify(segmentService).listSegmentsByTenant("tenant-a");
    }

    @Test
    void computeActiveSegmentUsesTenantContext() {
        TenantContext.set("tenant-a");
        UserSegment segment = new UserSegment("seg-1", "tenant-a", "Active",
                "desc", Map.of(), List.of(), 0, Instant.now());
        when(segmentService.computeActiveUsersSegment("tenant-a", 30)).thenReturn(segment);

        var response = controller.computeActiveSegment(30);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertEquals("tenant-a", response.getBody().tenantId());
    }

    @Test
    void computePowerUsersSegmentUsesTenantContext() {
        TenantContext.set("tenant-a");
        UserSegment segment = new UserSegment("seg-1", "tenant-a", "Power Users",
                "desc", Map.of(), List.of(), 0, Instant.now());
        when(segmentService.computePowerUsersSegment("tenant-a", 100)).thenReturn(segment);

        var response = controller.computePowerUsersSegment(100);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertEquals("tenant-a", response.getBody().tenantId());
    }

    @Test
    void tenantAUserCannotAccessTenantBAnalytics() {
        TenantContext.set("tenant-a");
        when(profileService.aggregateProfile("tenant-a", "user-1"))
                .thenReturn(new UserProfile("prof-1", "tenant-a", "user-1",
                        "User One", Set.of(), Map.of(), Map.of(), 0, 0, Instant.now(), Instant.now(), Instant.now()));

        controller.getProfile("user-1");

        verify(profileService).aggregateProfile("tenant-a", "user-1");
        verify(profileService, never()).aggregateProfile(eq("tenant-b"), any());
    }

    @Test
    void fakeXTenantIdHeaderDoesNotChangeAnalyticsTenant() {
        TenantContext.set("tenant-a");
        when(eventService.findEventsByTenant("tenant-a", 100))
                .thenReturn(List.of());

        controller.listEvents(100);

        verify(eventService).findEventsByTenant(eq("tenant-a"), anyInt());
        verify(eventService, never()).findEventsByTenant(eq("tenant-b"), anyInt());
    }
}
