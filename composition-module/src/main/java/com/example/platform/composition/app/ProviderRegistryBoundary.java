package com.example.platform.composition.app;

import com.example.platform.composition.domain.CompositionModels.CapabilityAvailability;
import java.util.*;

/** Internal boundary: manifests and provider/backend resolution never cross this interface. */
public interface ProviderRegistryBoundary {
    List<CapabilityAvailability> publicAvailability();
    Optional<CapabilityAvailability> resolve(String capabilityId, String version);
}
