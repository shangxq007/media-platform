package com.example.platform.federation.graphql.dataloader;

import com.example.platform.render.app.RenderJobService;
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
public class RenderJobArtifactDataLoader implements MappedBatchLoader<String, Map<String, Object>> {
    private static final Logger log = LoggerFactory.getLogger(RenderJobArtifactDataLoader.class);

    private final RenderJobService renderJobService;

    public RenderJobArtifactDataLoader(RenderJobService renderJobService) {
        this.renderJobService = renderJobService;
    }

    @Override
    public CompletionStage<Map<String, Map<String, Object>>> load(Set<String> keys) {
        log.debug("Batch loading {} render job artifacts", keys.size());
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Map<String, Object>> result = new HashMap<>();
            for (String jobId : keys) {
                try {
                    Object job = renderJobService.getById(jobId);
                    result.put(jobId, toMap(job));
                } catch (Exception e) {
                    Map<String, Object> fallback = new HashMap<>();
                    fallback.put("id", jobId);
                    result.put(jobId, fallback);
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
