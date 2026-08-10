package com.example.platform.storage.opendal;

import com.example.platform.storage.contract.StorageObjectId;

import java.util.Objects;

/**
 * Encodes and decodes opaque locators for OpenDAL-backed storage.
 *
 * <p>The locator format is: {@code <service-type>:<bucket>/<normalized-path>}
 *
 * <p>Properties:
 * <ul>
 *   <li>Opaque to domain layer — only the provider adapter interprets it</li>
 *   <li>Never contains credentials</li>
 *   <li>Deterministic for same physical location</li>
 *   <li>Path traversal patterns are rejected</li>
 *   <li>No signed URL query parameters allowed</li>
 * </ul>
 */
public final class OpenDalLocationCodec {

    private static final char SEPARATOR = ':';
    private static final char PATH_SEP = '/';

    private OpenDalLocationCodec() {
    }

    /**
     * Encodes a service type, bucket, and path into an opaque locator.
     *
     * @param serviceType "fs" or "s3"
     * @param bucket bucket name (may be empty for fs)
     * @param path normalized object path
     * @return opaque locator string
     */
    public static String encode(String serviceType, String bucket, String path) {
        Objects.requireNonNull(serviceType, "serviceType required");
        Objects.requireNonNull(path, "path required");
        validatePath(path);

        String normalizedPath = normalizePath(path);

        if (bucket == null || bucket.isEmpty()) {
            return serviceType + SEPARATOR + normalizedPath;
        }
        return serviceType + SEPARATOR + bucket + PATH_SEP + normalizedPath;
    }

    /**
     * Extracts the object path portion from an opaque locator.
     *
     * @param opaqueLocator the encoded locator
     * @return the path suitable for OpenDAL operations
     */
    public static String extractPath(String opaqueLocator) {
        Objects.requireNonNull(opaqueLocator, "opaqueLocator required");

        int sepIndex = opaqueLocator.indexOf(SEPARATOR);
        if (sepIndex < 0) {
            throw new IllegalArgumentException("Invalid locator format: missing service separator");
        }

        String afterService = opaqueLocator.substring(sepIndex + 1);

        // Strip bucket prefix if present (format: bucket/path)
        int pathSepIndex = afterService.indexOf(PATH_SEP);
        if (pathSepIndex >= 0) {
            return afterService.substring(pathSepIndex + 1);
        }
        return afterService;
    }

    /**
     * Extracts the service type from an opaque locator.
     */
    public static String extractServiceType(String opaqueLocator) {
        Objects.requireNonNull(opaqueLocator, "opaqueLocator required");

        int sepIndex = opaqueLocator.indexOf(SEPARATOR);
        if (sepIndex < 0) {
            throw new IllegalArgumentException("Invalid locator format: missing service separator");
        }
        return opaqueLocator.substring(0, sepIndex);
    }

    /**
     * Derives an OpenDAL path from a StorageObjectId.
     * Used for generating deterministic staging and commit paths.
     */
    public static String pathForObjectId(StorageObjectId objectId) {
        Objects.requireNonNull(objectId, "objectId required");
        return "objects/" + objectId.value();
    }

    /**
     * Generates a staging path for two-phase write.
     */
    public static String stagingPath(String writeSessionId) {
        return "staging/" + writeSessionId + ".tmp";
    }

    /**
     * Generates a commit path from a staging path.
     */
    public static String commitPath(String stagingPath, StorageObjectId objectId) {
        Objects.requireNonNull(objectId, "objectId required");
        return "objects/" + objectId.value();
    }

    /**
     * Validates that a path does not contain traversal or injection patterns.
     */
    static void validatePath(String path) {
        if (path.contains("..")) {
            throw new IllegalArgumentException("Path must not contain traversal patterns (..): " + path);
        }
        if (path.startsWith("/")) {
            throw new IllegalArgumentException("Path must not be absolute: " + path);
        }
        if (path.contains("\0")) {
            throw new IllegalArgumentException("Path must not contain null bytes");
        }
        if (path.contains("?") || path.contains("&")) {
            throw new IllegalArgumentException("Path must not contain query parameters");
        }
    }

    /**
     * Normalizes a path by stripping leading/trailing whitespace and collapsing multiple slashes.
     */
    static String normalizePath(String path) {
        String normalized = path.trim().replaceAll("/+", "/");
        // Remove leading slash if present after normalization
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
