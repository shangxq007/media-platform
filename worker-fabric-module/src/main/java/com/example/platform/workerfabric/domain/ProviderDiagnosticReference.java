package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** Typed opaque reference to provider-specific diagnostics kept outside canonical state. */
public record ProviderDiagnosticReference(String diagnosticType, String reference) {

    public ProviderDiagnosticReference {
        Objects.requireNonNull(diagnosticType, "diagnosticType");
        Objects.requireNonNull(reference, "reference");
        if (diagnosticType.isBlank() || reference.isBlank()) {
            throw new IllegalArgumentException("diagnostic type and reference must not be blank");
        }
    }
}
