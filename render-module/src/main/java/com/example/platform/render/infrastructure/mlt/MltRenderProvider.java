package com.example.platform.render.infrastructure.mlt;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.render.infrastructure.RenderPreset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MLT/melt-based render provider for multi-track timeline rendering.
 *
 * <p>This provider renders timelines using MLT's melt command. It converts
 * the internal timeline representation to MLT project XML and executes melt.</p>
 *
 * <p>Activated when {@code render.providers.melt.enabled=true}.</p>
 */
@Component
@ConditionalOnProperty(prefix = "render.providers.melt", name = "enabled", havingValue = "true")
public class MltRenderProvider implements RenderProvider {

    private static final Logger log = LoggerFactory.getLogger(MltRenderProvider.class);

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    private final ProcessToolRunner processToolRunner;
    private final MltProjectXmlBuilder xmlBuilder;
    private final MLTCommandFactory commandFactory;

    public MltRenderProvider(ProcessToolRunner processToolRunner,
                              MltProjectXmlBuilder xmlBuilder, MLTCommandFactory commandFactory) {
        this.processToolRunner = processToolRunner;
        this.xmlBuilder = xmlBuilder;
        this.commandFactory = commandFactory;
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

            // Parse timeline from aiScript
            Map<String, Object> timeline = parseTimeline(aiScript);

            // Build MLT XML
            String xmlContent = buildMltXml(timeline, preset);
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

    private String buildMltXml(Map<String, Object> timeline, RenderPreset preset) {
        // Build MLT XML from timeline data
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<mlt>\n");

        // Profile
        xml.append(String.format("  <profile width=\"%d\" height=\"%d\" frame_rate_num=\"%d\" />\n",
                preset.width(), preset.height(), preset.frameRate()));

        // Producers (from tracks/clips)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tracks = (List<Map<String, Object>>) timeline.getOrDefault("tracks", List.of());
        int producerId = 0;
        for (Map<String, Object> track : tracks) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> clips = (List<Map<String, Object>>) track.getOrDefault("children", List.of());
            for (Map<String, Object> clip : clips) {
                String sourceUrl = (String) clip.getOrDefault("media_reference", "");
                if (sourceUrl != null && !sourceUrl.isEmpty() && !sourceUrl.startsWith("file://")) {
                    String sourcePath = sourceUrl.replace("file://", "");
                    xml.append(String.format("  <producer id=\"prod_%d\"><property name=\"resource\">%s</property></producer>\n",
                            producerId, escapeXml(sourcePath)));
                    producerId++;
                }
            }
        }

        // If no producers, add a color producer
        if (producerId == 0) {
            xml.append("  <producer id=\"color\"><property name=\"resource\">color:#334455</property></producer>\n");
        }

        // Playlist
        xml.append("  <playlist id=\"main\">\n");
        for (int i = 0; i < Math.max(producerId, 1); i++) {
            xml.append(String.format("    <entry producer=\"prod_%d\" />\n", i));
        }
        xml.append("  </playlist>\n");

        // Tractor
        xml.append("  <tractor><multitrack><track producer=\"main\" /></multitrack></tractor>\n");
        xml.append("</mlt>\n");

        return xml.toString();
    }

    private void writeXmlFile(String path, String content) throws Exception {
        try (FileWriter writer = new FileWriter(path)) {
            writer.write(content);
        }
    }

    private String escapeXml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private Map<String, Object> parseTimeline(String aiScript) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(aiScript, Map.class);
        } catch (Exception e) {
            return Map.of("tracks", List.of());
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
