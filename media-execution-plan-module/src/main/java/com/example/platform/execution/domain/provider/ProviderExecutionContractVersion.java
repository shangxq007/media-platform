package com.example.platform.execution.domain.provider;

/** Version of the provider execution/SPI contract, distinct from capability contract versions. */
public record ProviderExecutionContractVersion(int major, int minor) {

    public ProviderExecutionContractVersion {
        if (major < 0 || minor < 0) {
            throw new IllegalArgumentException("provider execution contract version parts must be >= 0");
        }
    }

    public static ProviderExecutionContractVersion of(int major, int minor) {
        return new ProviderExecutionContractVersion(major, minor);
    }
}
