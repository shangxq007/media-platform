package com.example.platform.workerfabric.domain;

/** Frozen eventual-delivery order; only the last step can change canonical execution state. */
public enum DeliveryFlowStage {
    AUTHORITATIVE_DATABASE_TRANSACTION,
    OUTBOX,
    DISPATCHER,
    DURABLE_QUEUE_OR_MESSAGE_TRANSPORT,
    IDEMPOTENT_CONSUMER,
    DATABASE_FENCED_TRANSITION
}
