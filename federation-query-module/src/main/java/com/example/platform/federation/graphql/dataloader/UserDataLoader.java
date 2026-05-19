package com.example.platform.federation.graphql.dataloader;

import com.example.platform.identity.app.IdentityAccessService;
import org.dataloader.MappedBatchLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@Component
public class UserDataLoader implements MappedBatchLoader<String, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(UserDataLoader.class);

    private final IdentityAccessService identityAccessService;

    public UserDataLoader(IdentityAccessService identityAccessService) {
        this.identityAccessService = identityAccessService;
    }

    @Override
    public CompletionStage<Map<String, Map<String, Object>>> load(Set<String> keys) {
        log.debug("Batch loading {} users", keys.size());
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Map<String, Object>> result = new HashMap<>();
            for (String userId : keys) {
                try {
                    Map<String, Object> overview = identityAccessService.overview();
                    result.put(userId, overview);
                } catch (Exception e) {
                    Map<String, Object> fallback = new HashMap<>();
                    fallback.put("id", userId);
                    result.put(userId, fallback);
                }
            }
            return result;
        });
    }
}
