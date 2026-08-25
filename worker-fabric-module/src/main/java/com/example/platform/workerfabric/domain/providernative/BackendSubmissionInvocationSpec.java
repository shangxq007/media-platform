package com.example.platform.workerfabric.domain.providernative;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Minimal typed extension point for non-process provider/backend request mechanics. */
public record BackendSubmissionInvocationSpec(
        String submissionType,
        Map<String, String> typedFields) implements InvocationSpec {

    public BackendSubmissionInvocationSpec {
        if (submissionType == null || submissionType.isBlank()) {
            throw new ProviderNativeExecutionFailure(
                    ProviderNativeFailureCode.UNSUPPORTED_INVOCATION_FORM,
                    "backend submission invocation requires a typed submission identity");
        }
        Objects.requireNonNull(typedFields, "typedFields");
        TreeMap<String, String> canonical = new TreeMap<>();
        typedFields.forEach((key, value) -> canonical.put(
                Objects.requireNonNull(key, "typedFields key"),
                Objects.requireNonNull(value, "typedFields value")));
        typedFields = Map.copyOf(canonical);
    }

    @Override
    public InvocationKind kind() {
        return InvocationKind.BACKEND_SUBMISSION;
    }
}
