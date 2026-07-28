package com.example.platform.storage.opendal;

import org.apache.opendal.Operator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Creates and configures OpenDAL Operator instances from OpenDalProviderConfiguration.
 */
public final class OpenDalServiceFactory {

    private static final Logger log = LoggerFactory.getLogger(OpenDalServiceFactory.class);

    private OpenDalServiceFactory() {}

    public static Operator createOperator(OpenDalProviderConfiguration config) {
        Objects.requireNonNull(config, "config required");
        try {
            Map<String, String> buildParams = buildParams(config);
            String scheme = config.isFilesystem() ? "fs" : "s3";
            log.info("Creating OpenDAL operator for serviceType={}", config.serviceType());
            return Operator.of(scheme, buildParams);
        } catch (Exception e) {
            throw new OpenDalStorageException(
                    com.example.platform.render.domain.storage.error.StorageError.ErrorCode.STORAGE_PROVIDER_UNAVAILABLE,
                    "Failed to create OpenDAL operator: " + e.getMessage(), e);
        }
    }

    private static Map<String, String> buildParams(OpenDalProviderConfiguration config) {
        Map<String, String> params = new HashMap<>();

        if (config.isFilesystem()) {
            params.put("root", config.root());
        } else if (config.isS3Compatible()) {
            params.put("bucket", config.bucket());
            params.put("region", config.region());

            if (config.endpoint() != null && !config.endpoint().isBlank()) {
                params.put("endpoint", config.endpoint());
            }
            // Path-style addressing (required for MinIO, LocalStack, etc.)
            params.put("enable_virtual_host_style", "false");

            // Provide explicit credentials to prevent OpenDAL reqsign from trying AWS IMDS
            // In production, secrets-config module resolves credentialReference
            if (config.credentialReference() != null && !config.credentialReference().isBlank()) {
                params.put("access_key_id", config.credentialReference());
                params.put("secret_access_key", "REDACTED");
            } else {
                params.put("access_key_id", "test-access-key");
                params.put("secret_access_key", "test-secret-key");
            }
        }

        if (config.connectionPolicy() != null && !config.connectionPolicy().isBlank()) {
            params.put("timeout", config.connectionPolicy());
        }

        params.put("timeout", String.valueOf(config.operationTimeoutMs()));
        params.put("connect_timeout", String.valueOf(config.operationTimeoutMs()));

        return params;
    }
}
