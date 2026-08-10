package com.example.platform.storage.opendal;

import com.example.platform.storage.contract.StorageProviderId;
import java.io.Serializable;
import java.util.Objects;

/**
 * Configuration for an OpenDAL-backed storage provider.
 *
 * <p>This configuration is intentionally separate from runtime construction.
 * It captures only declarative parameters; {@link OpenDalServiceFactory} is responsible
 * for binding configuration to an initialized OpenDAL {@code Operator}.
 *
 * <p>{@code credentialReference} is a platform secrets-reference identifier.
 * It is NEVER plaintext credentials. Resolution is the responsibility of the
 * secrets-config module and is intentionally out of scope for this adapter.
 */
public record OpenDalProviderConfiguration(
        StorageProviderId providerId,
        String serviceType,
        String root,
        String region,
        String endpoint,
        String bucket,
        String credentialReference,
        String connectionPolicy,
        long operationTimeoutMs,
        int maxRetryAttempts
) implements Serializable {

    public OpenDalProviderConfiguration {
        Objects.requireNonNull(providerId, "providerId required");
        Objects.requireNonNull(serviceType, "serviceType required");
        if (serviceType.isBlank()) {
            throw new IllegalArgumentException("serviceType must not be blank");
        }
        if (!serviceType.equals("fs") && !serviceType.equals("s3")) {
            throw new IllegalArgumentException("Unsupported serviceType: " + serviceType + " (supported: fs, s3)");
        }
        if (serviceType.equals("fs")) {
            if (root == null || root.isBlank()) {
                throw new IllegalArgumentException("root is required for fs serviceType");
            }
        }
        if (serviceType.equals("s3")) {
            if (bucket == null || bucket.isBlank()) {
                throw new IllegalArgumentException("bucket is required for s3 serviceType");
            }
            if (region == null || region.isBlank()) {
                throw new IllegalArgumentException("region is required for s3 serviceType");
            }
        }
        if (operationTimeoutMs <= 0) {
            throw new IllegalArgumentException("operationTimeoutMs must be > 0");
        }
        if (maxRetryAttempts < 0) {
            throw new IllegalArgumentException("maxRetryAttempts must be >= 0");
        }
    }

    public OpenDalProviderConfiguration(StorageProviderId providerId, String serviceType, String root,
                                        String region, String endpoint, String bucket,
                                        String credentialReference, String connectionPolicy) {
        this(providerId, serviceType, root, region, endpoint, bucket, credentialReference,
                connectionPolicy, 30000L, 0);
    }

    public boolean isFilesystem() {
        return "fs".equals(serviceType);
    }

    public boolean isS3Compatible() {
        return "s3".equals(serviceType);
    }

    public String toSafeString() {
        return "OpenDalProviderConfiguration[provider=" + providerId.value()
                + ", service=" + serviceType
                + ", bucket=" + bucket
                + ", root=" + root
                + ", credentialRef=" + (credentialReference != null ? "[REDACTED]" : "none")
                + ", timeout=" + operationTimeoutMs + "ms"
                + ", retries=" + maxRetryAttempts
                + "]";
    }
}
