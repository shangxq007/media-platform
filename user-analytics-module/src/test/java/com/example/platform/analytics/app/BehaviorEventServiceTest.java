package com.example.platform.analytics.app;

import com.example.platform.analytics.domain.UserBehaviorEvent;
import com.example.platform.analytics.infrastructure.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BehaviorEventServiceTest {

    private BehaviorEventService service;
    private InMemoryUserBehaviorEventRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryUserBehaviorEventRepository();
        service = new BehaviorEventService(repository, new SimpleMeterRegistry());
    }

    @Test
    void ingestEventCreatesEventWithRequiredFields() {
        UserBehaviorEvent event = service.ingestEvent(
                "tenant-1", "user-1", "page_view", "view", "dashboard", null, Map.of());

        assertNotNull(event);
        assertNotNull(event.eventId());
        assertEquals("tenant-1", event.tenantId());
        assertEquals("user-1", event.userId());
        assertEquals("page_view", event.eventType());
        assertNotNull(event.occurredAt());
    }

    @Test
    void ingestEventStripsSensitiveMetadata() {
        UserBehaviorEvent event = service.ingestEvent(
                "tenant-1", "user-1", "action", "click", "button", null,
                Map.of("password", "secret123", "public-info", "safe-value"));

        assertNotNull(event);
        assertFalse(event.metadata().containsKey("password"));
        assertEquals("safe-value", event.metadata().get("public-info"));
    }

    @Test
    void ingestEventStripsIpAddressMetadata() {
        UserBehaviorEvent event = service.ingestEvent(
                "tenant-1", "user-1", "action", "click", "button", null,
                Map.of("ip", "192.168.1.1", "user-agent", "Mozilla/5.0",
                        "page", "dashboard"));

        assertNotNull(event);
        assertFalse(event.metadata().containsKey("ip"));
        assertFalse(event.metadata().containsKey("user-agent"));
        assertEquals("dashboard", event.metadata().get("page"));
    }

    @Test
    void ingestEventStripsTokenAndAuthMetadata() {
        UserBehaviorEvent event = service.ingestEvent(
                "tenant-1", "user-1", "api_call", "request", "endpoint", null,
                Map.of("token", "abc123", "authorization", "Bearer xyz",
                        "x-forwarded-for", "10.0.0.1", "safe-param", "value"));

        assertNotNull(event);
        assertFalse(event.metadata().containsKey("token"));
        assertFalse(event.metadata().containsKey("authorization"));
        assertFalse(event.metadata().containsKey("x-forwarded-for"));
        assertEquals("value", event.metadata().get("safe-param"));
    }

    @Test
    void findEventsByTenantAndUserReturnsMatchingEvents() {
        service.ingestEvent("tenant-1", "user-1", "view", "click", "page", null, Map.of());
        service.ingestEvent("tenant-1", "user-2", "view", "click", "page", null, Map.of());
        service.ingestEvent("tenant-1", "user-1", "action", "submit", "form", null, Map.of());

        List<UserBehaviorEvent> events = service.findEventsByTenantAndUser("tenant-1", "user-1", 10);
        assertEquals(2, events.size());
    }

    @Test
    void findEventsByTypeFiltersCorrectly() {
        service.ingestEvent("tenant-1", "user-1", "page_view", "view", "page", null, Map.of());
        service.ingestEvent("tenant-1", "user-1", "click", "click", "button", null, Map.of());
        service.ingestEvent("tenant-1", "user-1", "page_view", "view", "page2", null, Map.of());

        List<UserBehaviorEvent> events = service.findEventsByType("tenant-1", "page_view", 10);
        assertEquals(2, events.size());
    }
}
