package com.example.platform.render.infrastructure.ffmpeg;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.render.infrastructure.RenderProvider;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * @deprecated Use {@link FFmpegRenderProvider} instead. This class is retained for
 *             backward compatibility and delegates to FFmpegRenderProvider.
 */
@Component
@ConditionalOnProperty(prefix = "render.providers.ffmpeg", name = "enabled", havingValue = "true")
@Deprecated
public class FfmpegRenderProvider implements RenderProvider {

    private final FFmpegRenderProvider delegate;

    public FfmpegRenderProvider(ProcessToolRunner processToolRunner,
            FFmpegCommandFactory commandFactory) {
        this.delegate = new FFmpegRenderProvider(processToolRunner, commandFactory);
    }

    @Override
    public RenderResult render(String jobId, String aiScript, String profile) {
        return delegate.render(jobId, aiScript, profile);
    }

    @Override
    public List<String> getSupportedProfiles() {
        return delegate.getSupportedProfiles();
    }

    @Override
    public boolean supports(String capability) {
        return delegate.supports(capability);
    }

    @Override
    public EnvironmentValidationResult validateEnvironment() {
        return delegate.validateEnvironment();
    }
}
