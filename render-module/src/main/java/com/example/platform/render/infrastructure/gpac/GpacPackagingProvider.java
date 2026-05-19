package com.example.platform.render.infrastructure.gpac;

import com.example.platform.extension.app.ProcessToolRunner;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * @deprecated Use {@link GPACPackagingProvider} instead. This class is retained for
 *             backward compatibility and delegates to GPACPackagingProvider.
 */
@Component
@ConditionalOnProperty(prefix = "render.providers.gpac", name = "enabled", havingValue = "true")
@Deprecated
public class GpacPackagingProvider implements PackagingProvider {

    private final GPACPackagingProvider delegate;

    public GpacPackagingProvider(ProcessToolRunner processToolRunner,
            Mp4BoxCommandFactory commandFactory) {
        this.delegate = new GPACPackagingProvider(processToolRunner, commandFactory);
    }

    @Override
    public PackagingResult packageMedia(PackagingRequest request) {
        return delegate.packageMedia(request);
    }

    @Override
    public List<String> getSupportedFormats() {
        return delegate.getSupportedFormats();
    }

    @Override
    public boolean validateEnvironment() {
        return delegate.validateEnvironment();
    }
}
