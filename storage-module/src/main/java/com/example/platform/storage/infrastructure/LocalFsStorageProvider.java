package com.example.platform.storage.infrastructure;

import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.PutObjectCommand;
import com.example.platform.storage.domain.StorageObjectRef;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.example.platform.storage.domain.StoredObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(prefix = "storage.s3", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalFsStorageProvider implements BlobStorage {

    private static final String BUCKET_NAME_REGEX = "^[a-zA-Z0-9._-]+$";

    private final Path root;
    private final ConcurrentHashMap<String, Map<String, Object>> metadataStore = new ConcurrentHashMap<>();

    public LocalFsStorageProvider(@Value("${app.storage.local-root:./.data/storage}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public String code() {
        return "localFsStorageProvider";
    }

    @Override
    public StorageObjectRef put(PutObjectCommand command) {
        try {
            Path p = resolveSafePath(command.bucket(), command.objectKey());
            Files.createDirectories(p.getParent());
            Files.write(p, command.content());
            return new StorageObjectRef(code(), command.bucket(), command.objectKey());
        } catch (IOException e) {
            throw new IllegalStateException("failed to persist local object", e);
        }
    }

    @Override
    public boolean delete(String bucket, String objectKey) {
        try {
            Path p = resolveSafePath(bucket, objectKey);
            if (p.equals(root.resolve(bucket).normalize())) {
                return false;
            }
            return !Files.exists(p) || Files.deleteIfExists(p);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public List<StoredObject> listObjects(String bucket, String prefix, int maxKeys) {
        int limit = Math.max(1, maxKeys);
        assertValidBucket(bucket);
        Path base = root.resolve(bucket).normalize();
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        if (!base.startsWith(root)) {
            return List.of();
        }
        String normalizedPrefix = prefix == null ? "" : prefix.replace('\\', '/');
        if (normalizedPrefix.startsWith("/")) {
            normalizedPrefix = normalizedPrefix.substring(1);
        }
        Path start;
        if (normalizedPrefix.isBlank()) {
            start = base;
        } else {
            assertSafeObjectKey(normalizedPrefix);
            start = base.resolve(normalizedPrefix).normalize();
            if (!start.startsWith(base)) {
                return List.of();
            }
        }
        if (!Files.exists(start)) {
            return List.of();
        }
        List<StoredObject> objects = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(start)) {
            for (Path path : paths.filter(Files::isRegularFile).limit(limit).toList()) {
                Path relative = base.relativize(path);
                String key = relative.toString().replace('\\', '/');
                long size = Files.size(path);
                objects.add(new StoredObject(bucket, key, size));
            }
        } catch (IOException e) {
            return List.of();
        }
        return objects;
    }

    @Override
    public Optional<byte[]> get(String bucket, String objectKey) {
        try {
            Path p = resolveSafePath(bucket, objectKey);
            if (!Files.isRegularFile(p)) {
                return Optional.empty();
            }
            return Optional.of(Files.readAllBytes(p));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public String presign(String objectKey) {
        return presign("artifacts", objectKey);
    }

    @Override
    public String presign(String bucket, String objectKey) {
        Path p = resolveSafePath(bucket, objectKey);
        return p.toUri().toString();
    }

    public StorageObjectRef saveArtifact(String artifactId, byte[] content, Map<String, Object> metadata) {
        String safeArtifactId = sanitizePathSegment(artifactId);
        String objectKey = safeArtifactId + "/output.mp4";
        Path p = resolveSafePath("artifacts", objectKey);
        try {
            Files.createDirectories(p.getParent());
            Files.write(p, content);
            metadataStore.put(safeArtifactId, metadata);
            return new StorageObjectRef(code(), "artifacts", objectKey);
        } catch (IOException e) {
            throw new IllegalStateException("failed to save artifact", e);
        }
    }

    public byte[] getArtifact(String artifactId) {
        String safeArtifactId = sanitizePathSegment(artifactId);
        Path p = resolveSafePath("artifacts", safeArtifactId + "/output.mp4");
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read artifact", e);
        }
    }

    public Map<String, Object> getArtifactMetadata(String artifactId) {
        String safeArtifactId = sanitizePathSegment(artifactId);
        return metadataStore.getOrDefault(safeArtifactId, Map.of());
    }

    private Path resolveSafePath(String bucket, String objectKey) {
        assertValidBucket(bucket);
        assertSafeObjectKey(objectKey);
        Path bucketPath = root.resolve(bucket).normalize();
        Path resolved = bucketPath.resolve(objectKey).normalize();
        if (!resolved.startsWith(bucketPath)) {
            throw new IllegalArgumentException("Object key escapes bucket directory");
        }
        if (Files.exists(resolved)) {
            try {
                Path realPath = resolved.toRealPath();
                if (!realPath.startsWith(bucketPath)) {
                    throw new IllegalArgumentException("Object path resolves outside bucket via symbolic link");
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to resolve real path", e);
            }
        }
        return resolved;
    }

    private static void assertValidBucket(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("Bucket name cannot be null or blank");
        }
        if (!bucket.matches(BUCKET_NAME_REGEX)) {
            throw new IllegalArgumentException(
                    "Bucket name contains illegal characters: only [a-zA-Z0-9._-] are allowed");
        }
    }

    private static void assertSafeObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Object key cannot be null or blank");
        }
        if (objectKey.contains("\0")) {
            throw new IllegalArgumentException("Object key cannot contain null bytes");
        }
        String decoded = URLDecoder.decode(objectKey, StandardCharsets.UTF_8);
        if (decoded.contains("\0")) {
            throw new IllegalArgumentException("Object key cannot contain null bytes");
        }
        String forward = decoded.replace('\\', '/');
        if (forward.startsWith("/")) {
            throw new IllegalArgumentException("Object key cannot be an absolute path");
        }
        if (forward.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Object key cannot contain a Windows drive letter");
        }
        String[] rawSegments = forward.split("/");
        for (String segment : rawSegments) {
            if ("..".equals(segment)) {
                throw new IllegalArgumentException("Object key cannot contain path traversal sequences");
            }
        }
        String normalized = Path.of(forward).normalize().toString().replace('\\', '/');
        if (normalized.startsWith("/") || normalized.equals("..") || normalized.startsWith("../")) {
            throw new IllegalArgumentException("Object key cannot escape root directory");
        }
        String[] normSegments = normalized.split("/");
        for (String segment : normSegments) {
            if ("..".equals(segment)) {
                throw new IllegalArgumentException("Object key cannot contain path traversal sequences");
            }
        }
    }

    private static String sanitizePathSegment(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Path segment cannot be null or blank");
        }
        if (input.contains("\0")) {
            throw new IllegalArgumentException("Path segment cannot contain null bytes");
        }
        String decoded = URLDecoder.decode(input, StandardCharsets.UTF_8);
        if (decoded.contains("\0")) {
            throw new IllegalArgumentException("Path segment cannot contain null bytes");
        }
        String sanitized = decoded.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (sanitized.isBlank() || sanitized.replace("_", "").isBlank()) {
            throw new IllegalArgumentException("Path segment contains no valid characters after sanitization");
        }
        return sanitized;
    }
}
