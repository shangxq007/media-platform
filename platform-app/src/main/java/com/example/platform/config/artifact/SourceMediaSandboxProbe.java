package com.example.platform.config.artifact;

import com.example.platform.artifact.contract.ArtifactSourceFacts;
import com.example.platform.artifact.contract.ArtifactSourceProbePort;
import com.example.platform.media.domain.probe.MediaProbeNormalizer;
import com.example.platform.media.infrastructure.probe.FfprobeMediaProbeNormalizer;
import com.example.platform.media.domain.probe.MediaProbeObservation;
import com.example.platform.media.domain.stream.StreamKind;
import com.example.platform.sandbox.LocalSandboxProcess;
import com.example.platform.sandbox.SandboxCancellation;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.shared.time.ExactDecimalSeconds;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** The Artifact upload boundary probes verified bytes in the existing bounded local sandbox. */
@Component
public class SourceMediaSandboxProbe implements ArtifactSourceProbePort {
    private final MediaProbeNormalizer normalizer = new FfprobeMediaProbeNormalizer();

    @Override
    public ArtifactSourceFacts probe(ArtifactId id, Path verifiedSource, String mimeType) {
        Path workspace = null;
        Path source = null;
        try {
            workspace = Files.createTempDirectory("source-probe-").toAbsolutePath().normalize();
            source = workspace.resolve("source.bin");
            Files.copy(verifiedSource, source);
            var result = LocalSandboxProcess.execute(List.of("ffprobe", "-v", "error", "-show_format",
                            "-show_streams", "-of", "json", source.toString()), workspace, workspace,
                    Set.of(source), Map.of("PATH", "/usr/bin:/bin",
                            "LD_LIBRARY_PATH", "/usr/lib/x86_64-linux-gnu/blas:/usr/lib/x86_64-linux-gnu/lapack:/usr/lib/x86_64-linux-gnu",
                            "LANG", "C", "LC_ALL", "C"),
                    Duration.ofSeconds(15), 1024 * 1024, SandboxCancellation.never());
            if (result.failure().isPresent() || result.exitCode().isEmpty()
                    || result.exitCode().getAsInt() != 0 || result.stdout().truncated()) {
                throw new IllegalArgumentException("source probe failed or exceeded its limits");
            }
            var normalized = normalizer.normalize(new MediaProbeObservation("ffprobe",
                    result.stdout().utf8(), true, false, false, List.of(), null), id);
            if (normalized.duration() == null) throw new IllegalArgumentException("source duration unavailable");
            var rawStreams = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(result.stdout().bytes()).path("streams");
            var streams = new java.util.ArrayList<ArtifactSourceFacts.Stream>();
            for (var stream : normalized.streams()) {
                if (stream.kind() != StreamKind.VIDEO && stream.kind() != StreamKind.AUDIO) continue;
                var raw = rawStreams.path(stream.streamIndex());
                MediaTime streamDuration = duration(raw);
                if (streamDuration == null || !streamDuration.isGreaterThan(MediaTime.ZERO)) continue;
                if (streamDuration.isGreaterThan(normalized.duration())) streamDuration = normalized.duration();
                streams.add(new ArtifactSourceFacts.Stream(stream.id().value(),
                        stream.kind() == StreamKind.VIDEO ? ArtifactSourceFacts.Kind.VIDEO : ArtifactSourceFacts.Kind.AUDIO,
                        streamDuration, stream.kind() == StreamKind.VIDEO
                                ? SourceDisplayAspect.fromProbe(raw).orElse(null) : null));
            }
            int video = 0;
            int audio = 0;
            for (var raw : rawStreams) {
                switch (raw.path("codec_type").asText()) {
                    case "video" -> video++;
                    case "audio" -> audio++;
                    default -> { }
                }
            }
            return new ArtifactSourceFacts(normalized.duration(), streams, new ArtifactSourceFacts.StreamCounts(video, audio));
        } catch (java.io.IOException unavailable) {
            throw new IllegalStateException("source probe unavailable", unavailable);
        } finally {
            if (workspace != null) {
                try {
                    if (source != null) Files.deleteIfExists(source);
                    Files.deleteIfExists(workspace.resolve(".sandbox-tmp"));
                    Files.deleteIfExists(workspace.resolve(".sandbox-output"));
                    Files.deleteIfExists(workspace);
                } catch (java.io.IOException cleanup) {
                    throw new IllegalStateException("source probe cleanup failed", cleanup);
                }
            }
        }
    }

    private static MediaTime duration(JsonNode stream) {
        try {
            String ticks = stream.path("duration_ts").asText("");
            String timeBase = stream.path("time_base").asText("");
            if (!ticks.isBlank() && timeBase.matches("[1-9][0-9]*/[1-9][0-9]*")) {
                String[] parts = timeBase.split("/");
                return MediaTime.ofTicks(Math.multiplyExact(Long.parseLong(ticks), Long.parseLong(parts[0])),
                        Long.parseLong(parts[1]));
            }
            String seconds = stream.path("duration").asText("");
            return seconds.isBlank() ? null : ExactDecimalSeconds.toMediaTime(seconds);
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            return null;
        }
    }
}
