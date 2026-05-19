package com.example.platform.federation.graphql.dataloader;

import com.example.platform.identity.app.WorkspaceService;
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
public class WorkspaceDataLoader implements MappedBatchLoader<String, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(WorkspaceDataLoader.class);

    private final WorkspaceService workspaceService;

    public WorkspaceDataLoader(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @Override
    public CompletionStage<Map<String, Map<String, Object>>> load(Set<String> keys) {
        log.debug("Batch loading {} workspaces", keys.size());
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Map<String, Object>> result = new HashMap<>();
            for (String wsId : keys) {
                try {
                    Object ws = workspaceService.getWorkspace(wsId);
                    result.put(wsId, toMap(ws));
                } catch (Exception e) {
                    Map<String, Object> fallback = new HashMap<>();
                    fallback.put("id", wsId);
                    result.put(wsId, fallback);
                }
            }
            return result;
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object obj) {
        if (obj instanceof Map) return (Map<String, Object>) obj;
        Map<String, Object> map = new HashMap<>();
        map.put("value", obj.toString());
        return map;
    }
}
