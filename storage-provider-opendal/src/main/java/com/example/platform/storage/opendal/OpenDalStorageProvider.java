package com.example.platform.storage.opendal;

import com.example.platform.storage.contract.ContentDigest;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.storage.contract.namespace.StorageNamespace;
import com.example.platform.storage.contract.provider.*;
import com.example.platform.storage.contract.read.*;
import com.example.platform.storage.contract.write.StorageWriteSession;
import com.example.platform.storage.contract.write.WriteSessionResult;
import com.example.platform.storage.opendal.internal.AbstractOpenDalProvider;
import com.example.platform.storage.opendal.internal.OpenDalFilesystemProvider;
import com.example.platform.storage.opendal.internal.OpenDalS3CompatibleProvider;

/**
 * Public entry point for creating OpenDAL-backed storage providers.
 *
 * <p>This class is the only public API in the OpenDAL adapter module.
 * It exposes no OpenDAL types, no native error types, and no internal
 * implementation details. All operations go through the platform
 * {@link StorageProvider} SPI.
 *
 * <p>Usage:
 * <pre>{@code
 *   OpenDalProviderConfiguration config = new OpenDalProviderConfiguration(
 *       new StorageProviderId("local-fs"),
 *       "fs",
 *       "/tmp/storage",
 *       null, null, null, null, null
 *   );
 *   StorageProvider provider = OpenDalStorageProvider.create(config);
 * }</pre>
 */
public final class OpenDalStorageProvider {

    private OpenDalStorageProvider() {
    }

    /**
     * Creates a StorageProvider from the given configuration.
     *
     * @param configuration the OpenDAL provider configuration
     * @return a platform StorageProvider backed by OpenDAL
     * @throws IllegalArgumentException if configuration is invalid
     * @throws OpenDalStorageException if the OpenDAL operator cannot be created
     */
    public static StorageProvider create(OpenDalProviderConfiguration configuration) {
        if (configuration.isFilesystem()) {
            return new OpenDalFilesystemProvider(configuration);
        } else if (configuration.isS3Compatible()) {
            return new OpenDalS3CompatibleProvider(configuration);
        }
        throw new IllegalArgumentException("Unsupported serviceType: " + configuration.serviceType());
    }

    /**
     * Creates a filesystem-backed storage provider with explicit parameters.
     */
    public static StorageProvider createFilesystem(StorageProviderId providerId, String root) {
        OpenDalProviderConfiguration config = new OpenDalProviderConfiguration(
                providerId, "fs", root, null, null, null, null, null
        );
        return new OpenDalFilesystemProvider(config);
    }

    /**
     * Creates an S3-compatible storage provider with explicit parameters.
     */
    public static StorageProvider createS3Compatible(
            StorageProviderId providerId,
            String region,
            String bucket,
            String endpoint
    ) {
        OpenDalProviderConfiguration config = new OpenDalProviderConfiguration(
                providerId, "s3", null, region, endpoint, bucket, null, null
        );
        return new OpenDalS3CompatibleProvider(config);
    }

    /**
     * Wraps an existing internal provider as a public SPI provider.
     * Used when the internal provider has been pre-configured with a test operator.
     */
    public static StorageProvider wrap(AbstractOpenDalProvider internalProvider) {
        return internalProvider;
    }
}
