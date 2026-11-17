package com.example.platform.operation.batch;

/** Per-step result recorded in the batch result and in commit provenance. */
@org.springframework.modulith.NamedInterface("batch")
public record BatchStepOutcome(int index, String status, String planDigest) {

    public static final String APPLIED = "APPLIED";
    public static final String NO_OP = "NO_OP";

    public BatchStepOutcome {
        if (index < 0) {
            throw new IllegalArgumentException("step index must be non-negative");
        }
        if (!APPLIED.equals(status) && !NO_OP.equals(status)) {
            throw new IllegalArgumentException("unsupported step status: " + status);
        }
        if (planDigest == null || planDigest.isBlank()) {
            throw new IllegalArgumentException("planDigest required");
        }
    }
}
