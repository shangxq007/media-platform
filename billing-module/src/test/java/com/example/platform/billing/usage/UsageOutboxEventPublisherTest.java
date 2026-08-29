package com.example.platform.billing.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.shared.usage.ObservedRuntimeUsage;
import com.example.platform.shared.usage.OperationRef;
import com.example.platform.shared.usage.UsageDimension;
import com.example.platform.usage.app.ObservedRuntimeUsageOutboxPublisher;
import com.example.platform.usage.infrastructure.ObservedRuntimeUsageJdbcRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UsageOutboxEventPublisherTest {

    @Test
    void publisherUsesEffectivePersistedObservationForDurableEvent() {
        ObservedRuntimeUsageJdbcRepository repository =
                mock(ObservedRuntimeUsageJdbcRepository.class);
        OutboxEventService outbox = mock(OutboxEventService.class);
        ObservedRuntimeUsage attempted = mock(ObservedRuntimeUsage.class);
        ObservedRuntimeUsage saved = mock(ObservedRuntimeUsage.class);
        when(repository.append(attempted)).thenReturn(saved);
        when(saved.observedUsageId()).thenReturn("observed-existing");
        when(saved.tenantId()).thenReturn("tenant-a");
        when(saved.operationRef()).thenReturn(OperationRef.of("operation-a", "attempt-a"));
        when(saved.dimension()).thenReturn(UsageDimension.DURATION);
        when(saved.idempotencyKey()).thenReturn("observation-key");
        ObservedRuntimeUsageOutboxPublisher publisher =
                new ObservedRuntimeUsageOutboxPublisher(repository, outbox);

        assertSame(saved, publisher.appendWithOutbox(attempted));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(outbox).appendEvent(
                eq("OBSERVED_RUNTIME_USAGE"), eq("observed-existing"),
                eq("RUNTIME_USAGE_OBSERVED"), eq(1), payload.capture(),
                eq("observed-usage:tenant-a:observation-key"));
        assertEquals("observed-existing", payload.getValue().get("observedUsageId"));
        assertEquals("attempt-a", payload.getValue().get("attemptRef"));
    }
}
