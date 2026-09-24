package com.example.platform.composition.app;

import java.util.Map;

/** Caller intent for Composition planning; all authority facts are resolved server-side. */
public record CompositionAdmissionRequest(
        String workspaceSelection,
        String compositionId,
        String publishedVersion,
        String idempotencyKey,
        String requestHash,
        String cancellationPolicy,
        String retryPolicy,
        Map<String, Object> parameters) {
    public CompositionAdmissionRequest {
        require(workspaceSelection, "workspaceSelection"); require(compositionId, "compositionId");
        require(publishedVersion, "publishedVersion"); require(idempotencyKey, "idempotencyKey");
        require(cancellationPolicy, "cancellationPolicy");
        require(retryPolicy, "retryPolicy"); parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
    private static void require(String v, String n) { if (v == null || v.isBlank()) throw new IllegalArgumentException(n + " is required"); }
}
