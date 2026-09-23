package com.example.platform.composition.app;

import com.example.platform.composition.domain.CompositionModels.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

@Component
public final class InMemoryProviderRegistry implements ProviderRegistryBoundary {
    private final Map<String, CapabilityAvailability> entries = new LinkedHashMap<>();
    public InMemoryProviderRegistry() {
        register(new CapabilityAvailability("media.transcode", "1.0", new ContractRef("MediaAsset", "1"), new ContractRef("MediaAsset", "1"), Set.of("video"), Set.of("video/mp4", "video/webm"), Set.of(ExecutionMode.ASYNCHRONOUS, ExecutionMode.BATCH), Availability.AVAILABLE, "Transcode media assets", new CostEstimate(BigDecimal.ONE, "quota-unit", BigDecimal.ONE), new Reliability(true, true, 3), Set.of("media.template"), Set.of("media.application")));
        register(new CapabilityAvailability("media.thumbnail", "1.0", new ContractRef("MediaAsset", "1"), new ContractRef("ImageAsset", "1"), Set.of("image"), Set.of("video/mp4"), Set.of(ExecutionMode.ASYNCHRONOUS), Availability.AVAILABLE, "Create a representative thumbnail", new CostEstimate(new BigDecimal("0.1"), "quota-unit", new BigDecimal("0.1")), new Reliability(true, true, 2), Set.of("media.template"), Set.of("media.application")));
    }
    private void register(CapabilityAvailability c) { entries.put(c.capabilityId()+":"+c.version(), c); }
    public List<CapabilityAvailability> publicAvailability() { return List.copyOf(entries.values()); }
    public Optional<CapabilityAvailability> resolve(String id, String version) { return Optional.ofNullable(entries.get(id+":"+version)); }
}
