package com.example.platform.render.infrastructure.asset.provider;

import com.example.platform.outbox.app.*;
import com.example.platform.outbox.domain.TaskCapability;
import com.example.platform.render.domain.asset.semantic.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs whisper CLI via ExecutionBackend and parses JSON output into AsrResult.
 */
@Component
public class WhisperAsrProvider {

    private static final Logger log = LoggerFactory.getLogger(WhisperAsrProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ExecutionBackendRegistry backendRegistry;

    public WhisperAsrProvider(ExecutionBackendRegistry backendRegistry) {
        this.backendRegistry = backendRegistry;
    }

    public AsrResult transcribe(String audioPath, String model, String language, String jobId, String taskId) {
        ExecutionBackend backend = backendRegistry.resolve(TaskCapability.ASR)
                .orElseThrow(() -> new IllegalStateException("No execution backend for ASR"));

        List<String> args = new ArrayList<>(List.of("whisper", audioPath,
                "--model", model != null ? model : "base",
                "--output_format", "json",
                "--output_dir", "/tmp/whisper"));

        if (language != null && !language.isBlank()) {
            args.add("--language");
            args.add(language);
        }

        ExecutionRequest request = ExecutionRequest.of(jobId, taskId, TaskCapability.ASR, args, 300);
        log.info("Whisper ASR started: model={} file={}", model, audioPath);
        long start = System.currentTimeMillis();

        ExecutionResult result = backend.execute(request);

        long dur = System.currentTimeMillis() - start;
        if (!result.success()) {
            log.warn("Whisper ASR failed: exitCode={} error={}", result.exitCode(), result.errorMessage());
            throw new IllegalStateException("Whisper failed: " + result.errorMessage());
        }

        AsrResult asrResult = parseWhisperOutput(result.stdout());
        log.info("Whisper ASR complete: model={} language={} segments={} dur={}ms",
                model, asrResult.language(), asrResult.segments().size(), dur);
        return asrResult;
    }

    @SuppressWarnings("unchecked")
    private AsrResult parseWhisperOutput(String stdout) {
        try {
            var root = MAPPER.readValue(stdout, Map.class);
            String text = (String) root.getOrDefault("text", "");
            String language = (String) root.getOrDefault("language", "en");
            double duration = parseDouble(root.getOrDefault("duration", "0"));

            List<Map<String, Object>> segs = (List<Map<String, Object>>) root.getOrDefault("segments", List.of());
            List<AsrResult.AsrSegment> segments = segs.stream().map(s -> {
                long startMs = (long) (parseDouble(s.getOrDefault("start", "0")) * 1000);
                long endMs = (long) (parseDouble(s.getOrDefault("end", "0")) * 1000);
                String segText = ((String) s.getOrDefault("text", "")).trim();
                double conf = parseDouble(s.getOrDefault("confidence", "0"));
                return new AsrResult.AsrSegment(startMs, endMs, segText, conf);
            }).toList();

            return new AsrResult("whisper", "base", language, duration, 0, text.trim(), segments);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse whisper output: " + e.getMessage());
        }
    }

    private static double parseDouble(Object v) {
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0; }
    }
}
