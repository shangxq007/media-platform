package com.example.platform.render.domain.timeline.internal;

import java.util.List;
import java.util.Map;

public record IncrementalTask(
        String taskId,
        String type,
        String targetEntityKey,
        List<String> dependsOn,
        Map<String, String> parameters) {

    public static IncrementalTask fullRender(String reason) {
        return new IncrementalTask("full", "FULL_RENDER", "", List.of(),
                Map.of("reason", reason != null ? reason : ""));
    }
}
