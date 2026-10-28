package com.example.platform.workerfabric.admission;

import com.example.platform.outbox.api.event.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class ExecutionOutboxEvents implements OutboxEventCatalog {
    public static final OutboxEventType<CapabilityExecutionRequested> CAPABILITY_REQUESTED = new OutboxEventType<>(
            "execution.capability.requested", 1, "platform_execution", CapabilityExecutionRequested.class,
            CapabilityExecutionRequested::executionId, CapabilityExecutionRequested::tenantId);
    @Override public List<OutboxEventType<?>> types() { return List.of(CAPABILITY_REQUESTED); }
}
