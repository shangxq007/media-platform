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

import com.example.platform.outbox.testsupport.OutboxTestEvents;
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
        when(service.claimLeaseMillis()).thenReturn(60000L);
        when(service.markProcessed(any(OutboxClaim.class))).thenReturn(true);
        OutboxEventRouter router = OutboxTestEvents.router();
        dispatcher = new OutboxEventDispatcher(service, publisher, router, 3, new SimpleMeterRegistry());
    }

    @Test
    void processBatchReturnsZeroWhenNoPendingEvents() {
        List<Map<String, Object>> empty = List.of();
        when(service.pendingForDispatch(100)).thenReturn(empty);

        int processed = dispatcher.processBatch(100);

        assertEquals(0, processed);
        verify(service, never()).markProcessed(any(OutboxClaim.class));
        verify(service, never()).markFailedWithDetails(any(OutboxClaim.class), anyString(), anyString());
    }

    @Test
    void processOnceReturnsFalseWhenNotLockable() {
        when(service.claimForProcessing(eq("obx_test1"), anyString())).thenReturn(java.util.Optional.empty());

        boolean result = dispatcher.processOnce("obx_test1");

        assertFalse(result);
        verify(service, never()).markProcessed(any(OutboxClaim.class));
    }

    @Test
    void processOnceMarksFailedForUnknownEventType() {
        when(service.claimForProcessing(eq("obx_test2"), anyString())).thenReturn(java.util.Optional.of(claim("obx_test2")));
        when(service.readClaimedEvent(claim("obx_test2"))).thenReturn(Map.of(
                "id", "obx_test2",
                "event_type", "unknown.event.type",
                "event_version", 1, "aggregate_type", "fixture_job", "aggregate_id", "rj-1", "payload", "{}"
        ));

        boolean result = dispatcher.processOnce("obx_test2");

        assertFalse(result);
        verify(service).quarantine(eq(claim("obx_test2")), eq("UNSUPPORTED_EVENT_VERSION"), eq("Unsupported event type/version"));
        verify(service, never()).markProcessed(any(OutboxClaim.class));
    }

    @Test
    void processOnceMarksFailedOnEventParsingError() {
        when(service.claimForProcessing(eq("obx_test3"), anyString())).thenReturn(java.util.Optional.of(claim("obx_test3")));
        when(service.readClaimedEvent(claim("obx_test3"))).thenReturn(Map.of(
                "id", "obx_test3",
                "event_type", "fixture.created",
                "event_version", 1, "aggregate_type", "fixture_job", "aggregate_id", "rj-1", "payload", "not-valid-json"
        ));

        boolean result = dispatcher.processOnce("obx_test3");

        assertFalse(result);
        verify(service).quarantine(eq(claim("obx_test3")), eq("MALFORMED_EVENT_PAYLOAD"), anyString());
        verify(service, never()).markProcessed(any(OutboxClaim.class));
    }

    @Test
    void processOnceMarksProcessedOnSuccess() {
        when(service.claimForProcessing(eq("obx_test4"), anyString())).thenReturn(java.util.Optional.of(claim("obx_test4")));
        when(service.readClaimedEvent(claim("obx_test4"))).thenReturn(OutboxTestEvents.row("obx_test4"));

        boolean result = dispatcher.processOnce("obx_test4");

        assertTrue(result);
        verify(service, times(1)).markProcessed(eq(claim("obx_test4")));
        verify(publisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void processBatchRespectsLimit() {
        when(service.pendingForDispatch(1)).thenReturn(List.of(
                Map.of("id", "obx_l1", "event_type", "unknown.event.type", "event_version", 1, "aggregate_type", "fixture_job", "aggregate_id", "rj-1", "payload", "{}")
        ));
        when(service.claimForProcessing(eq("obx_l1"), anyString())).thenReturn(java.util.Optional.of(claim("obx_l1")));
        when(service.readClaimedEvent(claim("obx_l1"))).thenReturn(Map.of(
                "id", "obx_l1",
                "event_type", "unknown.event.type",
                "event_version", 1, "aggregate_type", "fixture_job", "aggregate_id", "rj-1", "payload", "{}"
        ));

        int processed = dispatcher.processBatch(1);

        assertEquals(0, processed);
        verify(service).quarantine(eq(claim("obx_l1")), eq("UNSUPPORTED_EVENT_VERSION"), eq("Unsupported event type/version"));
    }

    @Test
    void processBatchProcessesMultipleEvents() {
        when(service.claimForProcessing(eq("obx_m1"), anyString())).thenReturn(java.util.Optional.of(claim("obx_m1")));
        when(service.readClaimedEvent(claim("obx_m1"))).thenReturn(OutboxTestEvents.row("obx_m1"));
        when(service.claimForProcessing(eq("obx_m2"), anyString())).thenReturn(java.util.Optional.empty());

        when(service.pendingForDispatch(100)).thenReturn(List.of(
                Map.of("id", "obx_m1"),
                Map.of("id", "obx_m2")
        ));

        int processed = dispatcher.processBatch(100);

        assertEquals(1, processed);
        verify(service, times(1)).markProcessed(eq(claim("obx_m1")));
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
        when(service.claimForProcessing(eq("obx_r1"), anyString())).thenReturn(java.util.Optional.of(claim("obx_r1")));
        when(service.readClaimedEvent(claim("obx_r1"))).thenReturn(OutboxTestEvents.row("obx_r1"));
        when(service.claimForProcessing(eq("obx_r2"), anyString())).thenReturn(java.util.Optional.empty());

        int retried = dispatcher.retryDueEvents();

        verify(service, times(1)).resetDueFailedEvents();
        assertEquals(1, retried);
    }

    @Test
    void legacyDispatchBatchDelegatesToProcessBatch() {
        when(service.pendingForDispatch(100)).thenReturn(List.of());

        int processed = dispatcher.processBatch(100);

        assertEquals(0, processed);
    }
    private static OutboxClaim claim(String id){return new OutboxClaim(id,"fixture-claim");}
    @org.junit.jupiter.api.AfterEach void close(){dispatcher.close();}
}
