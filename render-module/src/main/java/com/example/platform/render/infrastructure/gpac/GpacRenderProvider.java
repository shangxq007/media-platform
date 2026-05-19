package com.example.platform.render.infrastructure.gpac;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.render.infrastructure.RenderProvider;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * @deprecated Use {@link GPACRenderProvider} instead. This class is retained for
 *             backward compatibility and delegates to GPACRenderProvider.
 */
@Component
@ConditionalOnProperty(prefix = "render.providers.gpac", name = "enabled", havingValue = "true")
@Deprecated
public class GpacRenderProvider implements RenderProvider {

    private final GPACRenderProvider delegate;

    public GpacRenderProvider(ProcessToolRunner processToolRunner,
                               Mp4BoxCommandFactory commandFactory) {
        this.delegate = new GPACRenderProvider(processToolRunner, commandFactory);
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
