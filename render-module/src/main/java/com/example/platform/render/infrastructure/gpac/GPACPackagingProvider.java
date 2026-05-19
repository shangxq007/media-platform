package com.example.platform.render.infrastructure.gpac;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "render.providers.gpac", name = "enabled", havingValue = "true")
public class GPACPackagingProvider implements PackagingProvider {

    private static final Logger log = LoggerFactory.getLogger(GPACPackagingProvider.class);

    private final ProcessToolRunner processToolRunner;
    private final Mp4BoxCommandFactory commandFactory;

    public GPACPackagingProvider(ProcessToolRunner processToolRunner,
            Mp4BoxCommandFactory commandFactory) {
        this.processToolRunner = processToolRunner;
        this.commandFactory = commandFactory;
    }

    @Override
    public PackagingResult packageMedia(PackagingRequest request) {
        log.info("GPACPackagingProvider: packaging input={} format={}",
                request.inputUri(), request.format());

        List<String> args;
        switch (request.format().toLowerCase()) {
            case "dash":
                args = commandFactory.buildDashCommand(
                        request.inputUri(),
                        request.outputBase() + "/manifest.mpd",
                        request.segmentDuration() * 1000);
                break;
            case "hls":
                args = commandFactory.buildHlsCommand(
                        request.inputUri(),
                        request.outputBase() + "/master.m3u8",
                        request.segmentDuration() * 1000);
                break;
            case "cmaf":
                args = commandFactory.buildCmafCommand(
                        request.inputUri(),
                        request.outputBase(),
                        request.segmentDuration() * 1000);
                break;
            default:
                return PackagingResult.failed("Unsupported format: " + request.format());
        }

        ToolExecutionRequest execRequest = ToolExecutionRequest.withTimeout(
                "MP4Box", args, 300_000);

        ToolExecutionResult result = processToolRunner.execute(execRequest);

        if (result.isSuccess()) {
            return PackagingResult.success(
                    request.outputBase() + "/manifest." + request.format(),
                    List.of(), request.format(), 0);
        } else {
            return PackagingResult.failed("MP4Box failed: " + result.stderr());
        }
    }

    @Override
    public List<String> getSupportedFormats() {
        return List.of("hls", "dash", "cmaf");
    }

    @Override
    public boolean validateEnvironment() {
        try {
            var validator = new GPACEnvironmentValidator(processToolRunner);
            return validator.validate();
        } catch (Exception e) {
            return false;
        }
    }
}
