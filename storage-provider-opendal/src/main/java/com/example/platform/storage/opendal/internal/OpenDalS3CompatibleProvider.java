package com.example.platform.storage.opendal.internal;

import com.example.platform.render.domain.storage.identity.StorageProviderId;
import com.example.platform.storage.opendal.OpenDalProviderConfiguration;
import com.example.platform.storage.opendal.OpenDalServiceFactory;
import org.apache.opendal.Operator;

/**
 * OpenDAL-backed storage provider for S3-compatible object storage.
 *
 * <p>Supports:
 * <ul>
 *   <li>Configurable endpoint (works with MinIO, LocalStack, etc.)</li>
 *   <li>Path-style addressing (no AWS-specific assumptions)</li>
 *   <li>Configurable region and bucket</li>
 *   <li>Credential reference (resolved externally by secrets module)</li>
 * </ul>
 *
 * <p>This provider makes no AWS-specific assumptions and works with any S3-compatible
 * backend via configurable endpoint.
 */
public final class OpenDalS3CompatibleProvider extends AbstractOpenDalProvider {

    private final OpenDalProviderConfiguration configuration;

    /**
     * Creates a new S3-compatible OpenDAL provider.
     *
     * @param configuration the provider configuration
     */
    public OpenDalS3CompatibleProvider(OpenDalProviderConfiguration configuration) {
        super(
                OpenDalServiceFactory.createOperator(configuration),
                configuration.providerId(),
                configuration.serviceType(),
                configuration.bucket(),
                configuration
        );
        this.configuration = configuration;
    }

    /**
     * Creates a provider with an externally constructed operator (for testing).
     */
    public OpenDalS3CompatibleProvider(OpenDalProviderConfiguration configuration, Operator operator) {
        super(operator, configuration.providerId(), configuration.serviceType(), configuration.bucket(), configuration);
        this.configuration = configuration;
    }

    public OpenDalProviderConfiguration configuration() {
        return configuration;
    }
}
