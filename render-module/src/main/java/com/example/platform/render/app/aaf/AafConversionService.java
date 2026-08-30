package com.example.platform.render.app.aaf;

import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.domain.standards.AafTimelineAdapter;
import com.example.platform.shared.Ids;
import com.example.platform.sandbox.LocalSandboxProcess;
import com.example.platform.sandbox.SandboxCancellation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Queues and processes AAF binary conversion via an explicitly configured external CLI.
 */
@Service
public class AafConversionService {

    private static final Logger log = LoggerFactory.getLogger(AafConversionService.class);

    private final ConcurrentLinkedQueue<AafConversionJob> queue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, AafConversionResult> results = new ConcurrentHashMap<>();

    @Value("${render.aaf.converter-enabled:false}")
    private boolean converterEnabled;

    @Value("${render.aaf.converter-command:}")
    private String converterCommand;

    @Value("${render.aaf.queue-max-depth:32}")
    private int maxDepth;

    public String enqueue(String aafPath, String defaultMediaUri, String tenantId) {
        if (!converterAvailable()) {
            throw new AafConversionUnavailableException();
        }
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
        if (!converterAvailable()) {
            return AafConversionResult.failed(job.conversionId(),
                    "AAF converter is disabled or converter-command is absent");
        }
        Path aafPath = Path.of(job.aafPath());
        if (!Files.exists(aafPath)) {
            return AafConversionResult.failed(job.conversionId(), "AAF file not found: " + job.aafPath());
        }

        Path outManifest = Files.createTempFile("aaf-manifest-", ".json");
        List<String> command = parseCommand(converterCommand, job.aafPath(), outManifest.toString());
        Path workspace = outManifest.toAbsolutePath().normalize().getParent();
        var process = LocalSandboxProcess.execute(command, workspace, workspace,
                Set.of(aafPath.toAbsolutePath().normalize()),
                Map.of("PATH", "/usr/bin:/bin", "LANG", "C"),
                java.time.Duration.ofMinutes(1), 1L << 20, SandboxCancellation.never());
        if (process.failure().isPresent()) {
            return AafConversionResult.failed(job.conversionId(),
                    process.failure().orElseThrow().code() + ": "
                            + process.failure().orElseThrow().message());
        }
        String manifest = Files.readString(outManifest);
        TimelineSpec spec = AafTimelineAdapter.importFromSource(
                job.aafPath(), manifest, job.defaultMediaUri());
        return AafConversionResult.success(job.conversionId(), manifest,
                spec.metadata().getOrDefault("platform.import.status", "CONVERTED"));
    }

    private boolean converterAvailable() {
        return converterEnabled && converterCommand != null && !converterCommand.isBlank();
    }

    private static List<String> parseCommand(String template, String input, String output) {
        if (template.isBlank() || template.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("converter-command must be one exact executable path");
        }
        return List.of(template, input, output);
    }
}
