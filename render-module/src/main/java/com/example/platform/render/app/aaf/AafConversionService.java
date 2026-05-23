package com.example.platform.render.app.aaf;

import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.standards.AafTimelineAdapter;
import com.example.platform.shared.Ids;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Queues and processes AAF binary conversion via external CLI (aaf2) or stub manifest generation.
 */
@Service
public class AafConversionService {

    private static final Logger log = LoggerFactory.getLogger(AafConversionService.class);

    private final ConcurrentLinkedQueue<AafConversionJob> queue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, AafConversionResult> results = new ConcurrentHashMap<>();

    @Value("${render.aaf.converter-enabled:true}")
    private boolean converterEnabled;

    @Value("${render.aaf.converter-command:}")
    private String converterCommand;

    @Value("${render.aaf.queue-max-depth:32}")
    private int maxDepth;

    public String enqueue(String aafPath, String defaultMediaUri, String tenantId) {
        String conversionId = Ids.newId("aaf");
        queue.offer(new AafConversionJob(conversionId, aafPath, defaultMediaUri, tenantId, Instant.now()));
        log.info("Enqueued AAF conversion {} path={} depth={}", conversionId, aafPath, queue.size());
        return conversionId;
    }

    public Optional<AafConversionJob> poll() {
        return Optional.ofNullable(queue.poll());
    }

    public Optional<AafConversionResult> getResult(String conversionId) {
        return Optional.ofNullable(results.get(conversionId));
    }

    public boolean canAcceptMore() {
        return queue.size() < maxDepth;
    }

    public AafConversionResult process(AafConversionJob job) {
        try {
            AafConversionResult result = runConversion(job);
            results.put(job.conversionId(), result);
            return result;
        } catch (Exception e) {
            AafConversionResult failed = AafConversionResult.failed(job.conversionId(), e.getMessage());
            results.put(job.conversionId(), failed);
            return failed;
        }
    }

    private AafConversionResult runConversion(AafConversionJob job) throws Exception {
        Path aafPath = Path.of(job.aafPath());
        if (!Files.exists(aafPath)) {
            return AafConversionResult.failed(job.conversionId(), "AAF file not found: " + job.aafPath());
        }

        if (converterEnabled && converterCommand != null && !converterCommand.isBlank()) {
            Path outManifest = Files.createTempFile("aaf-manifest-", ".json");
            List<String> command = parseCommand(converterCommand, job.aafPath(), outManifest.toString());
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                reader.lines().forEach(line -> log.debug("AAF converter: {}", line));
            }
            int exit = process.waitFor();
            if (exit != 0) {
                return AafConversionResult.failed(job.conversionId(),
                        "AAF converter exited with code " + exit);
            }
            String manifest = Files.readString(outManifest);
            TimelineSpec spec = AafTimelineAdapter.importFromSource(
                    job.aafPath(), manifest, job.defaultMediaUri());
            return AafConversionResult.success(job.conversionId(), manifest,
                    spec.metadata().getOrDefault("platform.import.status", "CONVERTED"));
        }

        String stubManifest = buildStubManifest(job);
        TimelineSpec spec = AafTimelineAdapter.parseJsonManifest(stubManifest, job.defaultMediaUri());
        return AafConversionResult.success(job.conversionId(), stubManifest,
                spec.metadata().getOrDefault("platform.import.status", "STUB_MANIFEST"));
    }

    private static String buildStubManifest(AafConversionJob job) {
        String media = job.defaultMediaUri() != null && !job.defaultMediaUri().isBlank()
                ? job.defaultMediaUri()
                : "file://" + job.aafPath();
        return """
                {
                  "id": "aaf-stub-%s",
                  "name": "AAF Stub Import",
                  "slots": [
                    {"id":"s1","mediaUri":"%s","duration":10,"timelineStart":0}
                  ]
                }
                """.formatted(job.conversionId(), media.replace("\"", "\\\""));
    }

    private static List<String> parseCommand(String template, String input, String output) {
        String cmd = template
                .replace("{input}", input)
                .replace("{output}", output);
        List<String> parts = new ArrayList<>();
        for (String token : cmd.split("\\s+")) {
            if (!token.isBlank()) {
                parts.add(token);
            }
        }
        return parts;
    }
}
