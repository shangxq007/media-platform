package com.example.platform.storage.opendal.internal;

import com.example.platform.render.domain.storage.identity.StorageProviderId;
import com.example.platform.storage.opendal.OpenDalProviderConfiguration;
import com.example.platform.storage.opendal.OpenDalServiceFactory;
import org.apache.opendal.Operator;

/**
 * OpenDAL-backed storage provider for local filesystem storage.
 *
 * <p>Supports:
 * <ul>
 *   <li>Configurable root directory</li>
 *   <li>Path normalization and traversal prevention (via {@link com.example.platform.storage.opendal.OpenDalLocationCodec})</li>
 *   <li>Immutable commit via atomic write to final path</li>
 * </ul>
 */
public final class OpenDalFilesystemProvider extends AbstractOpenDalProvider {

    private final OpenDalProviderConfiguration configuration;

    /**
     * Creates a new filesystem-backed OpenDAL provider.
     *
     * @param configuration the provider configuration
     */
    public OpenDalFilesystemProvider(OpenDalProviderConfiguration configuration) {
        super(
                OpenDalServiceFactory.createOperator(configuration),
                configuration.providerId(),
                configuration.serviceType(),
                "", // No bucket for filesystem
                configuration
        );
        this.configuration = configuration;
    }

    /**
     * Creates a provider with an externally constructed operator (for testing).
     */
    public OpenDalFilesystemProvider(OpenDalProviderConfiguration configuration, Operator operator) {
        super(operator, configuration.providerId(), configuration.serviceType(), "", configuration);
        this.configuration = configuration;
    }

    public OpenDalProviderConfiguration configuration() {
        return configuration;
    }
}
