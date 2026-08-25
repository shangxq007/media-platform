package com.example.platform.workerfabric.reuse;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;

/** Low-cardinality metrics emitted only at the production Phase 16 orchestration boundaries. */
public final class Phase16RuntimeMetrics {

    private static final String PREFIX = "media.worker_fabric.phase16.";
    private final MeterRegistry registry;

    public Phase16RuntimeMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public void reuseLookup(ValidatedReuseDecision.Outcome outcome) {
        increment("reuse.lookup", "outcome", outcome.name());
    }

    public void materialization(OperationOutcome outcome) {
        increment("materialization", "outcome", outcome.name());
    }

    public void staging(OperationOutcome outcome) {
        increment("staging", "outcome", outcome.name());
    }

    public void durablePublish(OperationOutcome outcome) {
        increment("durable.publish", "outcome", outcome.name());
    }

    public void artifactCommit(OperationOutcome outcome) {
        increment("artifact.commit", "outcome", outcome.name());
    }

    public void reusePublication(ReusePublicationResult outcome) {
        increment("reuse.publication", "outcome", outcome.name());
    }

    private void increment(String metric, String tagName, String tagValue) {
        Counter.builder(PREFIX + metric)
                .tag(tagName, tagValue)
                .register(registry)
                .increment();
    }

    public enum OperationOutcome {
        SUCCESS,
        FAILURE
    }
}
