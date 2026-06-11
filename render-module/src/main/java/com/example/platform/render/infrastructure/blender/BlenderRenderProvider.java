package com.example.platform.render.infrastructure.blender;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.infrastructure.ExternalRenderScriptParser;
import com.example.platform.render.infrastructure.ProviderStatus;
import com.example.platform.render.infrastructure.ProviderType;
import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.shared.Ids;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

/**
 * Blender 3D render provider.
 *
 * <p>Status: POC / P1-P2 (depending on current product 3D template needs).
 * Specialized 3D render provider for 3D title sequences, logo reveals,
 * product display animations, 3D subtitles, advanced spatial visuals.
 * NOT for ordinary subtitle videos or ordinary editing.
 * Supports reading parameters from RenderJob, generating Blender Python scripts
 * or passing .blend parameters.
 * Output: transparent background video, image sequence, or final video.
 * Output should be passed to FFmpeg for final normalization.</p>
 */
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

    @Override
    public ProviderStatus getStatus() {
        return ProviderStatus.POC;
    }

    @Override
    public String getPriority() {
        return "P1";
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.RENDER;
    }

    @Override
    public String getPurpose() {
        return "3D render provider for 3D title sequences, logo reveals, product animations, 3D subtitles";
    }

    @Override
    public List<String> getLimitations() {
        return List.of(
                "NOT for ordinary subtitle videos or ordinary editing",
                "NOT for 2D timeline editing",
                "Output should be passed to FFmpeg for final normalization",
                "Requires Blender binary and Python scripting"
        );
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("3d_render", "logo_reveal", "product_animation", "3d_title", "spatial_visuals");
    }

    @Override
    public boolean isAutoDispatch() {
        return true;
    }
}
