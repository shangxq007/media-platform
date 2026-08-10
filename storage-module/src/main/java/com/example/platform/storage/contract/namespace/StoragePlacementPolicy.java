package com.example.platform.storage.contract.namespace;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;
public record StoragePlacementPolicy(DataClassification classification, Set<String> allowedRegions, boolean crossRegionAllowed) implements Serializable {
    public StoragePlacementPolicy {
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(allowedRegions, "allowedRegions");
        allowedRegions = Set.copyOf(allowedRegions);
    }
}
