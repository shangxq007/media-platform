package com.example.platform.federation.graphql.dataloader;

import org.dataloader.MappedBatchLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@Component
public class ProviderHealthDataLoader implements MappedBatchLoader<String, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(ProviderHealthDataLoader.class);

    @Override
    public CompletionStage<Map<String, Map<String, Object>>> load(Set<String> keys) {
        log.debug("Batch loading health for {} providers", keys.size());
        return CompletableFuture.supplyAsync(() -> keys.stream()
                .collect(Collectors.toMap(
                        providerKey -> providerKey,
                        providerKey -> Map.<String, Object>of(
                                "providerKey", providerKey,
                                "status", "HEALTHY",
                                "latencyMs", 0,
                                "errorRate", 0.0
                        )
                )));
    }
}
