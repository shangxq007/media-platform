package com.example.platform.observability.context;

/** Read-only diagnostics. A snapshot is not an authenticated principal or an authorization decision. */
public interface ObservationContext {
    Snapshot snapshot();
    record Snapshot(String traceId, String requestId, String principal) {}
}
