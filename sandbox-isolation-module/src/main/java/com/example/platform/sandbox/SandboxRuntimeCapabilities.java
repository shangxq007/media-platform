package com.example.platform.sandbox;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Ephemeral runtime evidence created only by a concrete adapter probe or an unavailable result. */
@org.springframework.modulith.NamedInterface("API")
public final class SandboxRuntimeCapabilities {
    private final boolean available;
    private final Set<SandboxCapability> capabilities;
    private final String mechanism;
    private final Instant observedAt;

    private SandboxRuntimeCapabilities(
            boolean available,
            Set<SandboxCapability> capabilities,
            String mechanism,
            Instant observedAt) {
        this.available = available;
        this.capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        this.mechanism = Objects.requireNonNull(mechanism, "mechanism");
        this.observedAt = Objects.requireNonNull(observedAt, "observedAt");
        if (!available && !capabilities.isEmpty()) {
            throw new IllegalArgumentException("unavailable runtime cannot advertise capabilities");
        }
    }

    static SandboxRuntimeCapabilities detected(
            Set<SandboxCapability> capabilities, String mechanism, Instant observedAt) {
        return new SandboxRuntimeCapabilities(true, capabilities, mechanism, observedAt);
    }

    /** Package-scoped construction for pure resolver tests; launchers cannot consume this evidence. */
    static SandboxRuntimeCapabilities available(Set<SandboxCapability> capabilities) {
        return new SandboxRuntimeCapabilities(true, capabilities, "resolver-test", Instant.EPOCH);
    }

    public static SandboxRuntimeCapabilities unavailable(String mechanism) {
        return new SandboxRuntimeCapabilities(false, Set.of(), mechanism, Instant.now());
    }

    public boolean available() { return available; }
    public Set<SandboxCapability> capabilities() { return capabilities; }
    public String mechanism() { return mechanism; }
    public Instant observedAt() { return observedAt; }

    public boolean supports(SandboxCapability capability) {
        return available && capabilities.contains(capability);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SandboxRuntimeCapabilities that
                && available == that.available
                && capabilities.equals(that.capabilities)
                && mechanism.equals(that.mechanism)
                && observedAt.equals(that.observedAt);
    }

    @Override public int hashCode() {
        return Objects.hash(available, capabilities, mechanism, observedAt);
    }

    @Override public String toString() {
        return "SandboxRuntimeCapabilities[available=" + available + ", capabilities=" + capabilities
                + ", mechanism=" + mechanism + ", observedAt=" + observedAt + "]";
    }
}
