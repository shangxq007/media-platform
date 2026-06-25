package com.example.platform.render.infrastructure.asset.provider;

import com.example.platform.outbox.app.*;
import com.example.platform.outbox.domain.TaskCapability;
import com.example.platform.render.domain.asset.semantic.*;
import com.example.platform.render.infrastructure.asset.AssetSemanticMetadataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Real PROBE task handler — delegates execution to an ExecutionBackend.
 * Handler owns business logic (asset context). Backend owns execution (subprocess, BMF, OpenCue).
 */
@Component
public class ProbeTaskHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(ProbeTaskHandler.class);
    private final ExecutionBackendRegistry backendRegistry;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProbeTaskHandler(ExecutionBackendRegistry backendRegistry) {
        this.backendRegistry = backendRegistry;
    }

    @Override
    public TaskCapability capability() {
        return TaskCapability.PROBE;
    }

    @Override
    public void execute(TaskExecutionContext context) {
        String assetId = extractField(context.payload(), "assetId");
        String storageUri = extractField(context.payload(), "storageUri");
        if (storageUri == null || storageUri.isBlank()) {
            throw new IllegalStateException("No storageUri in task payload for asset: " + assetId);
        }

        ExecutionBackend backend = backendRegistry.resolve(TaskCapability.PROBE)
                .orElseThrow(() -> new IllegalStateException("No execution backend for PROBE"));

        List<String> args = List.of("ffprobe", "-v", "quiet", "-print_format", "json",
                "-show_format", "-show_streams", storageUri);

        ExecutionRequest request = ExecutionRequest.of(context.jobId(), context.taskId(),
                TaskCapability.PROBE, args, 30);

        log.info("ProbeTaskHandler: delegating probe for asset={} to backend={}", assetId, backend.backendId());
        ExecutionResult result = backend.execute(request);

        if (!result.success()) {
            throw new IllegalStateException("Probe failed: " + result.errorMessage());
        }

        log.info("ProbeTaskHandler: probe complete asset={} dur={}ms stdout={} chars",
                assetId, result.durationMs(), result.stdout() != null ? result.stdout().length() : 0);
    }

    private String extractField(String payload, String field) {
        try { return (String) mapper.readValue(payload, Map.class).getOrDefault(field, ""); }
        catch (Exception e) { return ""; }
    }
}
