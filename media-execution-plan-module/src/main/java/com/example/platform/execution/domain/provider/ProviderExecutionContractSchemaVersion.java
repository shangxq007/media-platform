package com.example.platform.execution.domain.provider;

/** Structural schema version for {@link ProviderExecutionContract} metadata. */
public record ProviderExecutionContractSchemaVersion(int value) {

    public ProviderExecutionContractSchemaVersion {
        if (value < 1) {
            throw new IllegalArgumentException("provider execution contract schema version must be >= 1");
        }
    }

    public static ProviderExecutionContractSchemaVersion of(int value) {
        return new ProviderExecutionContractSchemaVersion(value);
    }
}
