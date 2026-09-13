package com.example.platform.federation.graphql.dataloader;

import com.example.platform.identity.api.workspace.WorkspaceQueries;
import org.dataloader.MappedBatchLoader;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.*;

@Component
public class WorkspaceDataLoader implements MappedBatchLoader<String, Map<String, Object>> {
    private final WorkspaceQueries workspaces;

    public WorkspaceDataLoader(WorkspaceQueries workspaces) {
        this.workspaces = workspaces;
    }

    @Override
    public CompletionStage<Map<String, Map<String, Object>>> load(Set<String> keys) {
        // Execute owner authorization in the dispatching request context; the common
        // pool does not carry the authenticated actor or tenant. Never fabricate an ID
        // projection when the owner denies a read.
        try {
            Map<String, Map<String, Object>> result = new HashMap<>();
            for (String id : keys) {
                var workspace = workspaces.getWorkspace(id);
                Map<String, Object> fields = new HashMap<>();
                fields.put("id", workspace.id());
                fields.put("tenantId", workspace.tenantId());
                fields.put("name", workspace.name());
                fields.put("description", workspace.description());
                fields.put("planTier", workspace.planTier());
                fields.put("status", workspace.status());
                result.put(id, Collections.unmodifiableMap(fields));
            }
            return CompletableFuture.completedFuture(Map.copyOf(result));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
