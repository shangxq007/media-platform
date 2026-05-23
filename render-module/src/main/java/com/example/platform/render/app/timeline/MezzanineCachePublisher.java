package com.example.platform.render.app.timeline;

import com.example.platform.render.app.planner.PipelineTask;
import com.example.platform.render.app.planner.PipelineTaskType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Publishes mezzanine (final_compose / segment-stitch) cache entries for incremental reuse.
 */
@Service
public class MezzanineCachePublisher {

    public Optional<Map<String, String>> publish(String tenantId,
                                                 PipelineTask composeTask,
                                                 String localUri,
                                                 String remoteUri,
                                                 String contentHash) {
        if (composeTask == null || localUri == null || localUri.isBlank()) {
            return Optional.empty();
        }
        String cacheKey = composeTask.cacheKey() != null && !composeTask.cacheKey().isBlank()
                ? composeTask.cacheKey()
                : "mezzanine:" + composeTask.taskId();
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("taskId", composeTask.taskId());
        entry.put("uri", localUri);
        entry.put("remoteUri", remoteUri != null && !remoteUri.isBlank() ? remoteUri : localUri);
        entry.put("cacheKey", cacheKey);
        entry.put("scope", PipelineTaskType.FINAL_COMPOSE.name());
        if (contentHash != null && !contentHash.isBlank()) {
            entry.put("contentHash", contentHash);
        }
        return Optional.of(Map.copyOf(entry));
    }

    public static Optional<PipelineTask> findComposeTask(
            java.util.List<PipelineTask> tasks) {
        return tasks.stream()
                .filter(t -> t.type() == PipelineTaskType.FINAL_COMPOSE)
                .findFirst();
    }
}
