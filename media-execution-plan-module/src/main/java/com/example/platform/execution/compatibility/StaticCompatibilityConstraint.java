package com.example.platform.execution.compatibility;

import com.example.platform.execution.domain.provider.ProviderId;
import java.util.Objects;

/**
 * Immutable Phase-4-only constraints not already represented by the #21 physical unit.
 * These are static declarations, never observations about current runtime availability.
 */
public sealed interface StaticCompatibilityConstraint
        permits StaticCompatibilityConstraint.Codec,
                StaticCompatibilityConstraint.DeviceKind,
                StaticCompatibilityConstraint.ProviderRuntime,
                StaticCompatibilityConstraint.CrossProviderBoundary {

    String canonicalKey();

    record Codec(CodecId codecId) implements StaticCompatibilityConstraint {
        public Codec {
            Objects.requireNonNull(codecId, "codecId");
        }

        @Override
        public String canonicalKey() {
            return "CODEC:" + codecId.value();
        }
    }

    record DeviceKind(ProviderDeviceKind deviceKind) implements StaticCompatibilityConstraint {
        public DeviceKind {
            Objects.requireNonNull(deviceKind, "deviceKind");
        }

        @Override
        public String canonicalKey() {
            return "DEVICE_KIND:" + deviceKind.name();
        }
    }

    record ProviderRuntime(ProviderRuntimeClass runtimeClass) implements StaticCompatibilityConstraint {
        public ProviderRuntime {
            Objects.requireNonNull(runtimeClass, "runtimeClass");
        }

        @Override
        public String canonicalKey() {
            return "PROVIDER_RUNTIME_CLASS:" + runtimeClass.name();
        }
    }

    record CrossProviderBoundary(
            ProviderId upstreamProviderId,
            BoundaryContractId boundaryContractId) implements StaticCompatibilityConstraint {
        public CrossProviderBoundary {
            Objects.requireNonNull(upstreamProviderId, "upstreamProviderId");
            Objects.requireNonNull(boundaryContractId, "boundaryContractId");
        }

        @Override
        public String canonicalKey() {
            return "CROSS_PROVIDER_BOUNDARY:"
                    + upstreamProviderId.value() + ":" + boundaryContractId.value();
        }
    }

    record CodecId(String value) {
        public CodecId {
            requireCanonicalValue(value, "codec id");
        }

        public static CodecId of(String value) {
            return new CodecId(value);
        }
    }

    record BoundaryContractId(String value) {
        public BoundaryContractId {
            requireCanonicalValue(value, "boundary contract id");
        }

        public static BoundaryContractId of(String value) {
            return new BoundaryContractId(value);
        }
    }

    enum ProviderDeviceKind {
        CPU,
        GPU,
        MEDIA_ACCELERATOR,
        OTHER_ACCELERATOR
    }

    enum ProviderRuntimeClass {
        NATIVE_PROCESS,
        ISOLATED_PROCESS,
        CONTAINERIZED,
        REMOTE_SERVICE
    }

    private static void requireCanonicalValue(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank() || !value.equals(value.strip())
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(label + " must be a non-blank canonical value");
        }
    }
}
