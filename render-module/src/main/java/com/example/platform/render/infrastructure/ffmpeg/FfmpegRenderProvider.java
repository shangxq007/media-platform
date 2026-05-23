package com.example.platform.render.infrastructure.ffmpeg;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.render.infrastructure.RenderProvider;
import java.util.List;
/**
 * @deprecated Use {@link FFmpegRenderProvider} instead. This class is retained for
 *             backward compatibility and delegates to FFmpegRenderProvider.
 */
@Deprecated
public class FfmpegRenderProvider implements RenderProvider {

    private final FFmpegRenderProvider delegate;

    public FfmpegRenderProvider(ProcessToolRunner processToolRunner,
            FFmpegCommandFactory commandFactory,
            com.example.platform.render.domain.timeline.TimelineScriptParser timelineScriptParser) {
        this.delegate = new FFmpegRenderProvider(processToolRunner, commandFactory, timelineScriptParser);
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
