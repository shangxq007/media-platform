package com.example.platform.execution.domain.provider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable provider execution/SPI metadata. Capability entries are references to #16
 * contracts only; this type owns no capability semantics or lifecycle.
 */
public record ProviderExecutionContract(
        ProviderExecutionContractSchemaVersion schemaVersion,
        ProviderExecutionContractVersion contractVersion,
        List<ProviderCapabilityContractReference> capabilityContractReferences) {

    private static final Comparator<ProviderCapabilityContractReference> REFERENCE_ORDER =
            Comparator.comparing((ProviderCapabilityContractReference value) -> value.capabilityId().value())
                    .thenComparingInt(value -> value.contractVersionRange().min().major())
                    .thenComparingInt(value -> value.contractVersionRange().min().minor())
                    .thenComparingInt(value -> value.contractVersionRange().max().major())
                    .thenComparingInt(value -> value.contractVersionRange().max().minor());

    public ProviderExecutionContract {
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(contractVersion, "contractVersion");
        Objects.requireNonNull(capabilityContractReferences, "capabilityContractReferences");
        var canonical = new ArrayList<ProviderCapabilityContractReference>(capabilityContractReferences.size());
        for (ProviderCapabilityContractReference reference : capabilityContractReferences) {
            canonical.add(Objects.requireNonNull(reference, "capabilityContractReferences element"));
        }
        if (new HashSet<>(canonical).size() != canonical.size()) {
            throw new IllegalArgumentException("duplicate capability contract reference");
        }
        canonical.sort(REFERENCE_ORDER);
        capabilityContractReferences = List.copyOf(canonical);
    }
}
