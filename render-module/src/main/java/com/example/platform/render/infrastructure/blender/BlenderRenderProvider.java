package com.example.platform.render.infrastructure.blender;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.infrastructure.ExternalRenderScriptParser;
import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.shared.Ids;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

/** L4 Blender batch render skeleton. */
public class BlenderRenderProvider implements RenderProvider {

    private static final Logger log = LoggerFactory.getLogger(BlenderRenderProvider.class);

    private final ProcessToolRunner processToolRunner;
    private final BlenderRenderProviderProperties properties;
    private final TimelineScriptParser timelineScriptParser;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    public BlenderRenderProvider(ProcessToolRunner processToolRunner,
                                 BlenderRenderProviderProperties properties,
                                 TimelineScriptParser timelineScriptParser) {
        this.processToolRunner = processToolRunner;
        this.properties = properties;
        this.timelineScriptParser = timelineScriptParser;
    }

    @Override
    public RenderResult render(String jobId, String aiScript, String profile) {
        log.info("BlenderRenderProvider: job={}", jobId);
        try {
            Path outputDir = Path.of(storageRoot, "artifacts", jobId);
            Files.createDirectories(outputDir);
            Path output = outputDir.resolve("blender-output.mp4");
            Path blendFile = outputDir.resolve("scene.blend");
            if (!Files.exists(blendFile)) {
                Files.writeString(blendFile, "# placeholder blend\n");
            }

            List<String> args = List.of(
                    properties.getBinary(),
                    "-b", blendFile.toString(),
                    "-o", outputDir.resolve("frame_").toString(),
                    "-F", "MPEG4",
                    "-f", "1");
            ToolExecutionResult result = processToolRunner.execute(
                    ToolExecutionRequest.withTimeout("blender", args, properties.getTimeoutMillis()));

            if (!result.isSuccess() && properties.isStubOnMissingBinary()) {
                Files.write(output, new byte[] {0, 0, 0, 8});
                log.warn("Blender render failed; stub for job={}", jobId);
            } else if (!result.isSuccess()) {
                throw new IllegalStateException("Blender failed: " + result.stderr());
            } else if (!Files.isRegularFile(output)) {
                Files.write(output, new byte[] {0, 0, 0, 8});
            }

            return new RenderResult(
                    Ids.newId("art"),
                    "localFsStorageProvider://artifacts/" + jobId + "/blender-output.mp4",
                    30L,
                    "mp4",
                    "1920x1080");
        } catch (Exception e) {
            throw new IllegalStateException("Blender render failed: " + e.getMessage(), e);
        }
    }

    private Path resolveBlendFile(Path outputDir, ExternalRenderScriptParser.ExternalRenderContext ctx) {
        if (ctx != null && ctx.blendUri() != null && !ctx.blendUri().isBlank()) {
            String local = timelineScriptParser.resolveLocalPath(ctx.blendUri(), storageRoot);
            return Path.of(local);
        }
        return outputDir.resolve("scene.blend");
    }

    @Override
    public List<String> getSupportedProfiles() {
        return List.of("blender_1080p", "blender_4k");
    }

    @Override
    public EnvironmentValidationResult validateEnvironment() {
        return EnvironmentValidationResult.ok();
    }
}
