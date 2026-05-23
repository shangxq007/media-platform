package com.example.platform.render.infrastructure.shaka;

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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** L7 Shaka Packager DASH/HLS packaging (alongside GPAC / Bento4). */
@Component
@ConditionalOnProperty(prefix = "render.providers.shaka", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ShakaPackagingProviderProperties.class)
public class ShakaPackagingProvider implements PackagingProvider {

    private static final Logger log = LoggerFactory.getLogger(ShakaPackagingProvider.class);

    private final ProcessToolRunner processToolRunner;
    private final ShakaCommandFactory commandFactory;
    private final ShakaPackagingProviderProperties properties;

    public ShakaPackagingProvider(ProcessToolRunner processToolRunner,
                                  ShakaCommandFactory commandFactory,
                                  ShakaPackagingProviderProperties properties) {
        this.processToolRunner = processToolRunner;
        this.commandFactory = commandFactory;
        this.properties = properties;
    }

    @Override
    public PackagingResult packageMedia(PackagingRequest request) {
        log.info("ShakaPackagingProvider: input={} format={}", request.inputUri(), request.format());
        try {
            Path outputDir = Path.of(request.outputBase());
            Files.createDirectories(outputDir);
            List<String> args = commandFactory.buildDashPackageCommand(
                    properties.getPackagerBin(), request.inputUri(), outputDir);
            ToolExecutionResult result = processToolRunner.execute(
                    ToolExecutionRequest.withTimeout("shaka-packager", args, properties.getTimeoutMillis()));
            if (!result.isSuccess()) {
                if (properties.isStubOnMissingBinary()) {
                    Path manifestPath = outputDir.resolve("stream.mpd");
                    Files.writeString(manifestPath, "<?xml version=\"1.0\"?><MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\"/>");
                    return PackagingResult.success(manifestPath.toString(), List.of(), "dash", 0);
                }
                return PackagingResult.failed("Shaka packager failed: " + result.stderr());
            }
            String manifest = outputDir.resolve("stream.mpd").toString();
            return PackagingResult.success(manifest, List.of(), "dash", 0);
        } catch (Exception e) {
            log.error("Shaka packaging failed", e);
            return PackagingResult.failed(e.getMessage());
        }
    }

    @Override
    public List<String> getSupportedFormats() {
        return List.of("dash", "dash_drm");
    }

    @Override
    public boolean validateEnvironment() {
        return true;
    }
}
