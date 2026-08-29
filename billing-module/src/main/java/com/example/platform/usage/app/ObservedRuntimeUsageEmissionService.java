package com.example.platform.usage.app;

import com.example.platform.shared.usage.ObservedRuntimeUsage;
import com.example.platform.shared.usage.ObservedRuntimeUsageEmissionPort;
import org.springframework.stereotype.Service;

/** Runtime-facing adapter to the single durable observation append path. */
@Service
public class ObservedRuntimeUsageEmissionService implements ObservedRuntimeUsageEmissionPort {

    private final ObservedRuntimeUsageOutboxPublisher publisher;

    public ObservedRuntimeUsageEmissionService(ObservedRuntimeUsageOutboxPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public ObservedRuntimeUsage emit(ObservedRuntimeUsage observation) {
        return publisher.appendWithOutbox(observation);
    }
}
