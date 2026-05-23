package com.example.platform.render.infrastructure.bento4;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.render.infrastructure.gpac.PackagingProvider;
import com.example.platform.render.infrastructure.gpac.PackagingRequest;
import com.example.platform.render.infrastructure.gpac.PackagingResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "render.providers.bento4", name = "enabled", havingValue = "true")
public class Bento4PackagingProvider implements PackagingProvider {

    private static final Logger log = LoggerFactory.getLogger(Bento4PackagingProvider.class);

    private final ProcessToolRunner processToolRunner;
    private final Bento4CommandFactory commandFactory;
    private final Bento4PackagingProviderProperties properties;

    public Bento4PackagingProvider(ProcessToolRunner processToolRunner,
                                   Bento4CommandFactory commandFactory,
                                   Bento4PackagingProviderProperties properties) {
        this.processToolRunner = processToolRunner;
        this.commandFactory = commandFactory;
        this.properties = properties;
    }

    @Override
    public PackagingResult packageMedia(PackagingRequest request) {
        log.info("Bento4PackagingProvider: input={} format={}", request.inputUri(), request.format());

        try {
            Path outputDir = Path.of(request.outputBase());
            Files.createDirectories(outputDir);
            Path fragmented = outputDir.resolve("fragmented.mp4");

            List<String> fragmentArgs = commandFactory.buildFragmentCommand(
                    properties.getMp4fragmentBin(), request.inputUri(), fragmented.toString());
            ToolExecutionResult fragmentResult = processToolRunner.execute(
                    ToolExecutionRequest.withTimeout("mp4fragment", fragmentArgs, properties.getTimeoutMillis()));
            if (!fragmentResult.isSuccess()) {
                return PackagingResult.failed("mp4fragment failed: " + fragmentResult.stderr());
            }

            String format = request.format().toLowerCase();
            boolean hls = "hls".equals(format);
            List<String> dashArgs = commandFactory.buildMp4DashCommand(
                    properties.getMp4dashBin(),
                    fragmented.toString(),
                    outputDir,
                    format,
                    hls);
            ToolExecutionResult dashResult = processToolRunner.execute(
                    ToolExecutionRequest.withTimeout("mp4dash", dashArgs, properties.getTimeoutMillis()));
            if (!dashResult.isSuccess()) {
                return PackagingResult.failed("mp4dash failed: " + dashResult.stderr());
            }

            String manifest = hls
                    ? outputDir.resolve("master.m3u8").toString()
                    : outputDir.resolve("stream.mpd").toString();
            return PackagingResult.success(manifest, List.of(), format, 0);
        } catch (Exception e) {
            log.error("Bento4 packaging failed", e);
            return PackagingResult.failed(e.getMessage());
        }
    }

    @Override
    public List<String> getSupportedFormats() {
        return List.of("dash", "hls", "dash_drm");
    }

    @Override
    public boolean validateEnvironment() {
        // Binaries are optional until packaging runs; operator installs mp4fragment/mp4dash on workers.
        return true;
    }
}
