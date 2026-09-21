package com.example.platform.usage.api;

import com.example.platform.outbox.api.event.*;
import com.example.platform.usage.api.UsageDimension;
import java.util.List;
import java.util.Objects;

/** Usage-owned observation propagation; Billing remains only a consumer of observation facts. */
@org.springframework.modulith.NamedInterface("events")
public final class ObservedUsageEvents implements OutboxEventCatalog {
    public record RuntimeUsageObserved(String observedUsageId, String tenantId, String operationRef, String attemptRef, UsageDimension dimension) {
        public RuntimeUsageObserved { Objects.requireNonNull(observedUsageId); Objects.requireNonNull(tenantId); Objects.requireNonNull(operationRef); Objects.requireNonNull(dimension); }
    }
    public static final OutboxEventType<RuntimeUsageObserved> RUNTIME_USAGE_OBSERVED = new OutboxEventType<>(
            "RUNTIME_USAGE_OBSERVED", 1, "OBSERVED_RUNTIME_USAGE", RuntimeUsageObserved.class, RuntimeUsageObserved::observedUsageId, RuntimeUsageObserved::tenantId);
    @Override public List<OutboxEventType<?>> types() { return List.of(RUNTIME_USAGE_OBSERVED); }
}
