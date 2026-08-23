package com.example.platform.execution.domain.provider;

/** Version identity for an immutable provider capability feasibility profile. */
public record ProviderCapabilityProfileVersion(int major, int minor) {

    public ProviderCapabilityProfileVersion {
        if (major < 0 || minor < 0) {
            throw new IllegalArgumentException("provider capability profile version parts must be >= 0");
        }
    }

    public static ProviderCapabilityProfileVersion of(int major, int minor) {
        return new ProviderCapabilityProfileVersion(major, minor);
    }
}
