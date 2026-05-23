package com.example.platform.render.infrastructure.gpac;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.render.infrastructure.RenderProvider;
import java.util.List;
/**
 * @deprecated Use {@link GPACRenderProvider} instead. This class is retained for
 *             backward compatibility and delegates to GPACRenderProvider.
 */
@Deprecated
public class GpacRenderProvider implements RenderProvider {

    private final GPACRenderProvider delegate;

    public GpacRenderProvider(ProcessToolRunner processToolRunner,
                               Mp4BoxCommandFactory commandFactory,
                               com.example.platform.render.domain.timeline.TimelineScriptParser timelineScriptParser) {
        this.delegate = new GPACRenderProvider(processToolRunner, commandFactory, timelineScriptParser);
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
