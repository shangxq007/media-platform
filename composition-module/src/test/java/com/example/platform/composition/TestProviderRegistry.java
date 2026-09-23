package com.example.platform.composition;
import com.example.platform.composition.app.ProviderRegistryBoundary;
import com.example.platform.composition.domain.CompositionModels.*;
import java.math.BigDecimal;
import java.util.*;
final class TestProviderRegistry implements ProviderRegistryBoundary {
    private final List<CapabilityAvailability> values=List.of(
        new CapabilityAvailability("media.thumbnail","1.0",new ContractRef("MediaAsset","1"),new ContractRef("ImageAsset","1"),Set.of("image"),Set.of("video/mp4"),Set.of(ExecutionMode.ASYNCHRONOUS),Availability.AVAILABLE,"test",new CostEstimate(BigDecimal.ONE,"u",BigDecimal.ONE),new Reliability(true,true,1),Set.of(),Set.of()));
    public List<CapabilityAvailability> publicAvailability(){return values;}
    public Optional<CapabilityAvailability> resolve(String id,String version){return values.stream().filter(v->v.capabilityId().equals(id)&&v.version().equals(version)).findFirst();}
}
