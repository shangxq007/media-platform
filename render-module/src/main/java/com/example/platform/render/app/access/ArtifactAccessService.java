package com.example.platform.render.app.access;

import com.example.platform.render.infrastructure.storage.StorageReferenceRepository;
import com.example.platform.storage.infrastructure.S3ObjectMaterializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Access-layer service for Product/Artifact results.
 * 
 * Generates ephemeral access descriptors (e.g., signed URLs) on demand.
 * Never persists signed URLs. Never exposes provider internals.
 */
@Service
public class ArtifactAccessService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactAccessService.class);

    private final StorageReferenceRepository storageRefRepo;
    private final S3ObjectMaterializer s3Materializer;

    @Value("${storage.s3.signed-access.enabled:false}")
    private boolean signedAccessEnabled;

    @Value("${storage.s3.signed-access.ttl:15m}")
    private String signedAccessTtl;

    @Autowired
    public ArtifactAccessService(StorageReferenceRepository storageRefRepo,
                                  org.springframework.beans.factory.ObjectProvider<S3ObjectMaterializer> s3Provider) {
        this.storageRefRepo = storageRefRepo;
        this.s3Materializer = s3Provider.getIfAvailable();
    }

    /**
     * Create access descriptor for a storage reference.
     * 
     * @param storageReferenceId the storage reference ID
     * @param mimeType optional MIME type
     * @param filename optional filename
     * @return access descriptor
     */
    public AccessDescriptor createAccessDescriptor(String storageReferenceId, String mimeType, String filename) {
        if (storageReferenceId == null || storageReferenceId.isBlank()) {
            return AccessDescriptor.notFound("No storage reference");
        }

        var ref = storageRefRepo.findById(storageReferenceId);
        if (ref.isEmpty()) {
            return AccessDescriptor.notFound("Storage reference not found");
        }

        var storageRef = ref.get();
        String provider = storageRef.providerType();
        String bucket = storageRef.rootPath();
        String objectKey = storageRef.relativePath();

        // S3/R2 signed URL
        if ("S3".equalsIgnoreCase(provider) || "R2".equalsIgnoreCase(provider)) {
            if (!signedAccessEnabled || s3Materializer == null) {
                return AccessDescriptor.unsupported("Signed access not enabled");
            }
            try {
                java.time.Duration ttl = java.time.Duration.parse("PT" + signedAccessTtl);
                String url = s3Materializer.createPresignedGetUrl(bucket, objectKey, ttl);
                return new AccessDescriptor(null, null, AccessDescriptor.AccessType.SIGNED_URL, "GET", url,
                        Instant.now().plus(ttl), (int) ttl.getSeconds(),
                        mimeType, filename, storageRef.fileSize(), "READY", null, true);
            } catch (Exception e) {
                log.warn("Failed to generate signed URL: {}", e.getMessage());
                return AccessDescriptor.accessFailed("Access generation failed");
            }
        }

        // Local provider
        if ("LOCAL".equalsIgnoreCase(provider)) {
            return AccessDescriptor.unsupported("Local provider access not supported via signed URL");
        }

        return AccessDescriptor.unsupported("Unsupported provider: " + provider);
    }

    public record AccessDescriptor(
            String productId,
            String artifactId,
            AccessType accessType,
            String method,
            String url,
            Instant expiresAt,
            Integer ttlSeconds,
            String mimeType,
            String filename,
            Long sizeBytes,
            String status,
            String message,
            boolean redacted
    ) {
        public enum AccessType {
            SIGNED_URL, LOCAL_STREAM, UNSUPPORTED, NOT_READY, NOT_FOUND, ACCESS_FAILED
        }

        public static AccessDescriptor notFound(String message) {
            return new AccessDescriptor(null, null, AccessType.NOT_FOUND, null, null, null, null,
                    null, null, null, "NOT_FOUND", message, true);
        }

        public static AccessDescriptor unsupported(String message) {
            return new AccessDescriptor(null, null, AccessType.UNSUPPORTED, null, null, null, null,
                    null, null, null, "UNSUPPORTED", message, true);
        }

        public static AccessDescriptor accessFailed(String message) {
            return new AccessDescriptor(null, null, AccessType.ACCESS_FAILED, null, null, null, null,
                    null, null, null, "ACCESS_FAILED", message, true);
        }
    }
}
