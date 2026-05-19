package com.example.platform.federation.graphql.dataloader;

import com.example.platform.entitlement.app.EntitlementDecisionService;
import com.example.platform.entitlement.domain.AccessCheckRequest;
import org.dataloader.MappedBatchLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
public class EntitlementGrantDataLoader implements MappedBatchLoader<String, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(EntitlementGrantDataLoader.class);

    private final EntitlementDecisionService entitlementDecisionService;

    public EntitlementGrantDataLoader(EntitlementDecisionService entitlementDecisionService) {
        this.entitlementDecisionService = entitlementDecisionService;
    }

    @Override
    public CompletionStage<Map<String, Map<String, Object>>> load(Set<String> keys) {
        log.debug("Batch loading entitlement decisions for {} feature keys", keys.size());
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Map<String, Object>> result = new HashMap<>();
            for (String featureKey : keys) {
                try {
                    var decision = entitlementDecisionService.evaluate(
                            new AccessCheckRequest(
                                    null, null, null, null, null,
                                    null, null, null, featureKey,
                                    null, null, null, null, null));
                    Map<String, Object> map = new HashMap<>();
                    map.put("allowed", decision.allowed());
                    map.put("reasonCode", decision.reasonCode() != null ? decision.reasonCode() : "");
                    map.put("tier", decision.currentTier() != null ? decision.currentTier() : "");
                    result.put(featureKey, map);
                } catch (Exception e) {
                    Map<String, Object> fallback = new HashMap<>();
                    fallback.put("featureKey", featureKey);
                    fallback.put("allowed", false);
                    result.put(featureKey, fallback);
                }
            }
            return result;
        });
    }
}
