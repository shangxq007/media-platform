package com.example.platform.render.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RenderQualityCheckService {
    private static final Logger log = LoggerFactory.getLogger(RenderQualityCheckService.class);

    private final MediaProbeService probeService;

    public RenderQualityCheckService(MediaProbeService probeService) {
        this.probeService = probeService;
    }

    public QualityCheckResult check(String jobId, String outputPath, RenderProviderProfile profile) {
        log.info("QualityCheck: checking job={} against profile={}", jobId, profile.name());

        MediaProbeResult report = probeService.probe(jobId, outputPath);

        if (!report.valid()) {
            log.warn("QualityCheck: probe failed for job={}: {}", jobId, report.errorMessage());
            return new QualityCheckResult(jobId, false, "PROBE_FAILED: " + report.errorMessage(),
                    profile.name(), report, List.of());
        }

        java.util.List<String> failures = new java.util.ArrayList<>();
        java.util.List<String> warnings = new java.util.ArrayList<>();

        if (report.width() != profile.width() || report.height() != profile.height()) {
            failures.add(String.format("Resolution mismatch: expected %dx%d, got %dx%d",
                    profile.width(), profile.height(), report.width(), report.height()));
        }

        if (profile.videoCodec() != null && !profile.videoCodec().equalsIgnoreCase(report.videoCodec())) {
            failures.add(String.format("Codec mismatch: expected %s, got %s",
                    profile.videoCodec(), report.videoCodec()));
        }

        if (report.durationMs() < 100) {
            failures.add("Output too short: " + report.durationMs() + "ms");
        }

        if (report.fileSizeBytes() < 100) {
            failures.add("Output file too small: " + report.fileSizeBytes() + " bytes");
        }

        if (Math.abs(report.frameRate() - profile.frameRate()) > 1.0) {
            warnings.add(String.format("Frame rate deviation: expected %d, got %.1f",
                    profile.frameRate(), report.frameRate()));
        }

        boolean passed = failures.isEmpty();
        String message = passed ? "QUALITY_CHECK_PASSED" : "QUALITY_CHECK_FAILED: " + String.join("; ", failures);

        log.info("QualityCheck: job={} result={}", jobId, message);

        return new QualityCheckResult(jobId, passed, message, profile.name(), report,
                passed ? warnings : failures);
    }

    public record QualityCheckResult(
            String jobId,
            boolean passed,
            String message,
            String profile,
            MediaProbeResult probeReport,
            java.util.List<String> issues
    ) {}
}
