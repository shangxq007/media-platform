package com.example.platform.storage.contract.provider;
import com.example.platform.storage.contract.StorageProviderId;
import java.io.Serializable;
import java.util.*;
public record StorageProviderCapabilities(StorageProviderId providerId, Map<ProviderCapability, CapabilitySupport> capabilities) implements Serializable {
    public StorageProviderCapabilities {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(capabilities, "capabilities");
        capabilities = Map.copyOf(capabilities);
    }
    public boolean supports(ProviderCapability cap) { return capabilities.get(cap) == CapabilitySupport.SUPPORTED; }
    public boolean supportsOrEmulated(ProviderCapability cap) {
        CapabilitySupport s = capabilities.get(cap);
        return s == CapabilitySupport.SUPPORTED || s == CapabilitySupport.EMULATED;
    }
}
