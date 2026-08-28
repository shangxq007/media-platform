package com.example.platform.render.app.timeline;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.render.domain.interchange.TimelineScriptParser;
import com.example.platform.render.domain.planning.FinalComposerHint;
import com.example.platform.render.infrastructure.RenderPreset;
import com.example.platform.render.infrastructure.RenderProviderRegistry;
import com.example.platform.render.infrastructure.mlt.MLTCommandFactory;
import com.example.platform.render.infrastructure.mlt.MltEnvironmentValidator;
import com.example.platform.render.infrastructure.mlt.MltProjectXmlBuilder;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Stitches ordered segment MP4 artifacts through the MLT provider path.
 */
@Service
public class SegmentStitchComposeService {

    private static final Logger log = LoggerFactory.getLogger(SegmentStitchComposeService.class);

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    private final ProcessToolRunner processToolRunner;
    private final TimelineScriptParser timelineScriptParser;
    private final MltProjectXmlBuilder mltProjectXmlBuilder;
    private final MLTCommandFactory mltCommandFactory;
    private final java.util.Optional<MltEnvironmentValidator> mltEnvironmentValidator;
    private final java.util.Optional<RenderProviderRegistry> providerRegistry;

    public SegmentStitchComposeService(ProcessToolRunner processToolRunner,
                                         TimelineScriptParser timelineScriptParser,
                                         MltProjectXmlBuilder mltProjectXmlBuilder,
                                         MLTCommandFactory mltCommandFactory,
                                         java.util.Optional<MltEnvironmentValidator> mltEnvironmentValidator,
                                         java.util.Optional<RenderProviderRegistry> providerRegistry) {
        this.processToolRunner = processToolRunner;
        this.timelineScriptParser = timelineScriptParser;
        this.mltProjectXmlBuilder = mltProjectXmlBuilder;
        this.mltCommandFactory = mltCommandFactory;
        this.mltEnvironmentValidator = mltEnvironmentValidator;
        this.providerRegistry = providerRegistry;
    }

    public StitchResult stitch(String jobId,
                               Map<String, String> orderedSegmentUris,
                               String profile) {
        return stitch(jobId, orderedSegmentUris, profile, FinalComposerHint.MLT);
    }

    public StitchResult stitch(String jobId,
                               Map<String, String> orderedSegmentUris,
                               String profile,
                               FinalComposerHint composer) {
        if (orderedSegmentUris == null || orderedSegmentUris.isEmpty()) {
            throw new IllegalArgumentException("No segment artifacts to stitch");
        }
        if (shouldUseMlt(composer)) {
            try {
                return stitchWithMlt(jobId, orderedSegmentUris, profile);
            } catch (Exception e) {
                throw new IllegalStateException("MLT segment stitch failed for job=" + jobId, e);
            }
        }
        throw new IllegalStateException("Typed provider plugin is required for non-MLT segment composition");
    }

    private boolean shouldUseMlt(FinalComposerHint composer) {
        if (composer != FinalComposerHint.MLT && composer != FinalComposerHint.AUTO) {
            return false;
        }
        boolean meltOk = mltEnvironmentValidator.map(MltEnvironmentValidator::validate).orElse(false);
        boolean providerOk = providerRegistry
                .map(r -> r.getProvider("mlt").isPresent())
                .orElse(false);
        return meltOk && providerOk;
    }

    public StitchResult stitchWithMlt(String jobId,
                                      Map<String, String> orderedSegmentUris,
                                      String profile) throws Exception {
        Path outputDir = Path.of(storageRoot, "artifacts", jobId);
        Files.createDirectories(outputDir);
        Path outputPath = outputDir.resolve("segment-stitch-output.mp4");
        RenderPreset preset = RenderPreset.fromProfile(profile);

        List<MltProjectXmlBuilder.SegmentMediaEntry> entries = new ArrayList<>();
        for (Map.Entry<String, String> entry : orderedSegmentUris.entrySet()) {
            if (!timelineScriptParser.mediaFileExists(entry.getValue(), storageRoot)) {
                log.warn("MLT segment stitch: missing file for {} uri={}", entry.getKey(), entry.getValue());
                continue;
            }
            String local = timelineScriptParser.resolveLocalPath(entry.getValue(), storageRoot);
            entries.add(new MltProjectXmlBuilder.SegmentMediaEntry(entry.getKey(), toFileUri(local)));
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("No local segment files for MLT stitch");
        }

        String xml = mltProjectXmlBuilder.buildSegmentConcat(
                entries, preset.width(), preset.height(), preset.frameRate());
        String xmlPath = outputDir.resolve("segment-stitch.mlt").toString();
        try (FileWriter writer = new FileWriter(xmlPath)) {
            writer.write(xml);
        }

        List<String> args = mltCommandFactory.buildRenderCommand(
                xmlPath, outputPath.toString(), preset.width(), preset.height(),
                preset.frameRate(), preset.videoCodec(), preset.audioCodec());
        ToolExecutionResult result = processToolRunner.execute(
                ToolExecutionRequest.withTimeout("melt", args, 600_000));
        if (!result.isSuccess()) {
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-500-005", 500505,
                            Map.of("en", "MLT segment stitch failed", "zh", "MLT段拼接失败"),
                            "render", 500),
                    "melt segment stitch failed: " + result.stderr(),
                    Map.of("jobId", jobId),
                    "en");
        }

        String storageUri = "localFsStorageProvider://artifacts/" + jobId + "/segment-stitch-output.mp4";
        log.info("Segment stitch (mlt) complete job={} segments={} uri={}", jobId, entries.size(), storageUri);
        return new StitchResult(Ids.newId("art"), storageUri, entries.size(), "mlt");
    }

    private static String toFileUri(String localPath) {
        if (localPath.startsWith("file:")) {
            return localPath;
        }
        return "file://" + localPath;
    }

    public record StitchResult(String artifactId, String storageUri, int segmentCount, String backend) {
        public StitchResult(String artifactId, String storageUri, int segmentCount) {
            this(artifactId, storageUri, segmentCount, "ffmpeg");
        }
    }
}
