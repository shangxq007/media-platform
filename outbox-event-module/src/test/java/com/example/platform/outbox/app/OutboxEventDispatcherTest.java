package com.example.platform.outbox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class OutboxEventDispatcherTest {

    private OutboxEventService service;
    private ApplicationEventPublisher publisher;
    private OutboxEventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        service = mock(OutboxEventService.class);
        publisher = mock(ApplicationEventPublisher.class);
        dispatcher = new OutboxEventDispatcher(service, publisher, 3, new SimpleMeterRegistry());
    }

    @Test
    void processBatchReturnsZeroWhenNoPendingEvents() {
        List<Map<String, Object>> empty = List.of();
        when(service.pendingForDispatch(100)).thenReturn(empty);

        int processed = dispatcher.processBatch(100);

        assertEquals(0, processed);
        verify(service, never()).markProcessed(anyString());
        verify(service, never()).markFailedWithDetails(anyString(), anyString(), anyString());
    }

    @Test
    void processOnceReturnsFalseWhenNotLockable() {
        when(service.lockForProcessing(eq("obx_test1"), anyString())).thenReturn(false);

        boolean result = dispatcher.processOnce("obx_test1");

        assertFalse(result);
        verify(service, never()).markProcessed(anyString());
    }

    @Test
    void processOnceMarksFailedForUnknownEventType() {
        when(service.lockForProcessing(eq("obx_test2"), anyString())).thenReturn(true);
        when(service.readEvent("obx_test2")).thenReturn(Map.of(
                "id", "obx_test2",
                "event_type", "unknown.event.type",
                "payload", "{}"
        ));

        boolean result = dispatcher.processOnce("obx_test2");

        assertFalse(result);
        verify(service, times(1)).markFailedWithDetails(eq("obx_test2"), eq("UNKNOWN_EVENT_TYPE"),
                eq("No handler for event type: unknown.event.type"));
        verify(service, never()).markProcessed(anyString());
    }

    @Test
    void processOnceMarksFailedOnEventParsingError() {
        when(service.lockForProcessing(eq("obx_test3"), anyString())).thenReturn(true);
        when(service.readEvent("obx_test3")).thenReturn(Map.of(
                "id", "obx_test3",
                "event_type", "render.job.created",
                "payload", "not-valid-json"
        ));

        boolean result = dispatcher.processOnce("obx_test3");

        assertFalse(result);
        verify(service, times(1)).markFailedWithDetails(anyString(),
                eq("DISPATCH_ERROR"), anyString());
        verify(service, never()).markProcessed(anyString());
    }

    @Test
    void processOnceMarksProcessedOnSuccess() {
        when(service.lockForProcessing(eq("obx_test4"), anyString())).thenReturn(true);
        when(service.readEvent("obx_test4")).thenReturn(Map.of(
                "id", "obx_test4",
                "event_type", "render.job.created",
                "payload", "{\"renderJobId\":\"rj-1\",\"projectId\":\"p-1\",\"timelineSnapshotId\":\"ts-1\",\"profile\":\"default\",\"primaryBackend\":\"ffmpeg\"}"
        ));

        boolean result = dispatcher.processOnce("obx_test4");

        assertTrue(result);
        verify(service, times(1)).markProcessed(eq("obx_test4"));
        verify(publisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void processBatchRespectsLimit() {
        when(service.pendingForDispatch(1)).thenReturn(List.of(
                Map.of("id", "obx_l1", "event_type", "unknown.event.type", "payload", "{}")
        ));
        when(service.lockForProcessing(eq("obx_l1"), anyString())).thenReturn(true);
        when(service.readEvent("obx_l1")).thenReturn(Map.of(
                "id", "obx_l1",
                "event_type", "unknown.event.type",
                "payload", "{}"
        ));

        int processed = dispatcher.processBatch(1);

        assertEquals(0, processed);
        verify(service, times(1)).markFailedWithDetails(eq("obx_l1"), eq("UNKNOWN_EVENT_TYPE"),
                eq("No handler for event type: unknown.event.type"));
    }

    @Test
    void processBatchProcessesMultipleEvents() {
        when(service.lockForProcessing(eq("obx_m1"), anyString())).thenReturn(true);
        when(service.readEvent("obx_m1")).thenReturn(Map.of(
                "id", "obx_m1",
                "event_type", "render.job.created",
                "payload", "{\"renderJobId\":\"rj-1\",\"projectId\":\"p-1\",\"timelineSnapshotId\":\"ts-1\",\"profile\":\"default\",\"primaryBackend\":\"ffmpeg\"}"
        ));
        when(service.lockForProcessing(eq("obx_m2"), anyString())).thenReturn(false);

        when(service.pendingForDispatch(100)).thenReturn(List.of(
                Map.of("id", "obx_m1"),
                Map.of("id", "obx_m2")
        ));

        int processed = dispatcher.processBatch(100);

        assertEquals(1, processed);
        verify(service, times(1)).markProcessed(eq("obx_m1"));
    }

    @Test
    void deadLetterDelegatesToService() {
        dispatcher.deadLetter("obx_dl1", "Manual test");

        verify(service, times(1)).markDeadLetter(eq("obx_dl1"), eq("Manual test"));
    }

    @Test
    void retryDueEventsResetsAndProcesses() {
        when(service.resetDueFailedEvents()).thenReturn(2);
        when(service.pendingForDispatch(100)).thenReturn(List.of(
                Map.of("id", "obx_r1"),
                Map.of("id", "obx_r2")
        ));
        when(service.lockForProcessing(eq("obx_r1"), anyString())).thenReturn(true);
        when(service.readEvent("obx_r1")).thenReturn(Map.of(
                "id", "obx_r1",
                "event_type", "render.job.created",
                "payload", "{\"renderJobId\":\"rj-1\",\"projectId\":\"p-1\",\"timelineSnapshotId\":\"ts-1\",\"profile\":\"default\",\"primaryBackend\":\"ffmpeg\"}"
        ));
        when(service.lockForProcessing(eq("obx_r2"), anyString())).thenReturn(false);

        int retried = dispatcher.retryDueEvents();

        verify(service, times(1)).resetDueFailedEvents();
        assertEquals(1, retried);
    }

    @Test
    void legacyDispatchBatchDelegatesToProcessBatch() {
        when(service.pendingForDispatch(100)).thenReturn(List.of());

        int processed = dispatcher.dispatchBatch(100);

        assertEquals(0, processed);
    }
}
