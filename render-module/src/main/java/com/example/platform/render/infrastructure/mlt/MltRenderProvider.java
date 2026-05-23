package com.example.platform.render.infrastructure.mlt;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.render.infrastructure.RenderPreset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MLT/melt-based render provider for multi-track timeline rendering.
 *
 * <p>This provider renders timelines using MLT's melt command. It converts
 * the internal timeline representation to MLT project XML and executes melt.</p>
 *
 * <p>Activated when {@code render.providers.mlt.enabled=true}.</p>
 */
@Component
@ConditionalOnProperty(prefix = "render.providers.mlt", name = "enabled", havingValue = "true")
public class MltRenderProvider implements RenderProvider {

    private static final Logger log = LoggerFactory.getLogger(MltRenderProvider.class);

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    private final ProcessToolRunner processToolRunner;
    private final MltProjectXmlBuilder xmlBuilder;
    private final MLTCommandFactory commandFactory;
    private final TimelineScriptParser timelineScriptParser;

    public MltRenderProvider(ProcessToolRunner processToolRunner,
                              MltProjectXmlBuilder xmlBuilder,
                              MLTCommandFactory commandFactory,
                              TimelineScriptParser timelineScriptParser) {
        this.processToolRunner = processToolRunner;
        this.xmlBuilder = xmlBuilder;
        this.commandFactory = commandFactory;
        this.timelineScriptParser = timelineScriptParser;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    @Override
    public RenderResult render(String jobId, String aiScript, String profile) {
        log.info("MltRenderProvider: rendering job={}, profile={}", jobId, profile);

        try {
            Path outputDir = Path.of(storageRoot, "artifacts", jobId);
            outputDir.toFile().mkdirs();
            String outputPath = outputDir.resolve("output.mp4").toString();
            RenderPreset preset = RenderPreset.fromProfile(profile);

            String xmlContent = buildMltXml(aiScript, preset);
            String xmlPath = outputDir.resolve("project.mlt").toString();
            writeXmlFile(xmlPath, xmlContent);

            // Build melt command
            List<String> args = commandFactory.buildRenderCommand(
                    xmlPath, outputPath, preset.width(), preset.height(),
                    preset.frameRate(), preset.videoCodec(), preset.audioCodec());

            // Execute melt
            ToolExecutionRequest request = ToolExecutionRequest.withTimeout("melt", args, 600_000);
            ToolExecutionResult result = processToolRunner.execute(request);

            if (result.isSuccess()) {
                String artifactId = Ids.newId("art");
                String resolution = preset.width() + "x" + preset.height();

                log.info("MltRenderProvider: render complete, artifact={}, resolution={}", artifactId, resolution);

                return new RenderResult(
                        artifactId,
                        "localFsStorageProvider://artifacts/" + jobId + "/output.mp4",
                        30L,
                        "mp4",
                        resolution
                );
            } else {
                throw new PlatformException(
                        new ConfigurableErrorCode("RENDER-500-004", 500504,
                                Map.of("en", "MLT melt rendering failed", "zh", "MLT melt渲染失败"),
                                "render", 500),
                        "melt failed: " + result.stderr(),
                        Map.of("jobId", jobId, "provider", "mlt", "exitCode", String.valueOf(result.exitCode())),
                        "en"
                );
            }
        } catch (PlatformException e) {
            throw e;
        } catch (Exception e) {
            log.error("MltRenderProvider: render failed for job={}", jobId, e);
            if (e instanceof NullPointerException) {
                log.error("MltRenderProvider: NullPointerException - likely mock not configured properly");
            }
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-500-001", 500101,
                            Map.of("en", "MLT render failed", "zh", "MLT渲染失败"),
                            "render", 500),
                    e.getMessage(),
                    Map.of("jobId", jobId, "provider", "mlt", "error", e.getClass().getSimpleName()),
                    "en"
            );
        }
    }

    private String buildMltXml(String aiScript, RenderPreset preset) {
        Optional<TimelineSpec> parsed = timelineScriptParser.parse(aiScript);
        if (parsed.isPresent()) {
            TimelineSpec timeline = resolveTimelinePaths(parsed.get());
            if (!timelineScriptParser.videoClipsInOrder(timeline).isEmpty()) {
                return xmlBuilder.build(timeline);
            }
        }
        return xmlBuilder.buildSkeleton(preset.width(), preset.height(), preset.frameRate());
    }

    private TimelineSpec resolveTimelinePaths(TimelineSpec timeline) {
        List<com.example.platform.render.domain.timeline.TimelineTrack> tracks = new ArrayList<>();
        if (timeline.tracks() != null) {
            for (var track : timeline.tracks()) {
                List<com.example.platform.render.domain.timeline.TimelineClip> clips = new ArrayList<>();
                if (track.clips() != null) {
                    for (var clip : track.clips()) {
                        if (clip.assetRef() == null) {
                            clips.add(clip);
                            continue;
                        }
                        String uri = clip.assetRef().storageUri();
                        String local = timelineScriptParser.resolveLocalPath(uri, storageRoot);
                        clips.add(new com.example.platform.render.domain.timeline.TimelineClip(
                                clip.id(),
                                com.example.platform.render.domain.timeline.TimelineAssetRef.of(
                                        clip.assetRef().assetId(), local),
                                clip.timelineStart(),
                                clip.assetInPoint(),
                                clip.assetOutPoint(),
                                clip.clipDuration(),
                                clip.effects()));
                    }
                }
                tracks.add(new com.example.platform.render.domain.timeline.TimelineTrack(
                        track.id(), track.name(), track.type(), track.layer(),
                        clips, track.muted(), track.locked()));
            }
        }
        return new TimelineSpec(
                timeline.id(), timeline.name(), timeline.description(),
                tracks, timeline.textOverlays(), timeline.outputSpec(),
                timeline.totalDuration(), timeline.metadata());
    }

    private void writeXmlFile(String path, String content) throws Exception {
        try (FileWriter writer = new FileWriter(path)) {
            writer.write(content);
        }
    }

    @Override
    public List<String> getSupportedProfiles() {
        return List.of("social_1080p", "social_720p", "default_1080p", "default_720p");
    }

    @Override
    public boolean supports(String capability) {
        return switch (capability) {
            case "timeline", "multi-track", "transitions", "compositing" -> true;
            default -> getSupportedProfiles().contains(capability);
        };
    }

    @Override
    public EnvironmentValidationResult validateEnvironment() {
        try {
            var validator = new MltEnvironmentValidator(processToolRunner);
            if (validator.validate()) {
                log.info("MltRenderProvider: melt available");
                return EnvironmentValidationResult.ok();
            }
            return EnvironmentValidationResult.failed("melt not available");
        } catch (Exception e) {
            log.warn("MltRenderProvider: melt not available: {}", e.getMessage());
            return EnvironmentValidationResult.failed("melt not available: " + e.getMessage());
        }
    }
}
