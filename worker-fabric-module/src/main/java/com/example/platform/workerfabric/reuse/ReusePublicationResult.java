package com.example.platform.workerfabric.reuse;

/** Result of the fenced pending/public activation protocol. */
public enum ReusePublicationResult {
    STAGED_PENDING,
    PENDING_IDEMPOTENT,
    ACTIVATED_WINNER,
    WINNER_IDEMPOTENT,
    STALE_OWNER_REJECTED,
    COMPLETION_NOT_AUTHORITATIVE_REJECTED,
    CONFLICT_REJECTED
}
