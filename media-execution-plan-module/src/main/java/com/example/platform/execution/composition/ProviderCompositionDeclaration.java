package com.example.platform.execution.composition;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import java.util.Objects;

/** Explicit immutable provider-native composition declaration for one exact binding. */
public record ProviderCompositionDeclaration(
        ProviderBindingPin providerBindingPin,
        NativePipelineSupport nativePipelineSupport) {

    public ProviderCompositionDeclaration {
        Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        Objects.requireNonNull(nativePipelineSupport, "nativePipelineSupport");
    }

    public enum NativePipelineSupport {
        SUPPORTED,
        UNSUPPORTED,
        UNKNOWN
    }
}
