package com.example.platform.workerfabric.domain;

/** Durable-message consumer which delegates only to idempotent observation ingestion. */
@FunctionalInterface
public interface IdempotentObservationConsumerPort {

    ExecutionObservationIngestionPort.IngestionResult consume(ExecutionObservation observation);
}
