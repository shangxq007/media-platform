package com.example.platform.storage.infrastructure;

import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.PutObjectCommand;
import com.example.platform.storage.domain.StorageObjectRef;
import java.io.IOException;
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

    private final Path root;
    private final ConcurrentHashMap<String, Map<String, Object>> metadataStore = new ConcurrentHashMap<>();

    public LocalFsStorageProvider(@Value("${app.storage.local-root:./.data/storage}") String root) {
        this.root = Path.of(root);
    }

    @Override
    public String code() {
        return "localFsStorageProvider";
    }

    @Override
    public StorageObjectRef put(PutObjectCommand command) {
        try {
            Path p = root.resolve(command.bucket()).resolve(command.objectKey());
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
            Path p = root.resolve(bucket).resolve(objectKey);
            return !Files.exists(p) || Files.deleteIfExists(p);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public List<StoredObject> listObjects(String bucket, String prefix, int maxKeys) {
        int limit = Math.max(1, maxKeys);
        Path base = root.resolve(bucket);
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        String normalizedPrefix = prefix == null ? "" : prefix.replace('\\', '/');
        if (normalizedPrefix.startsWith("/")) {
            normalizedPrefix = normalizedPrefix.substring(1);
        }
        Path start = normalizedPrefix.isBlank() ? base : base.resolve(normalizedPrefix);
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
            Path p = root.resolve(bucket).resolve(objectKey);
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
        return root.resolve(bucket).resolve(objectKey).toUri().toString();
    }

    public StorageObjectRef saveArtifact(String artifactId, byte[] content, Map<String, Object> metadata) {
        String objectKey = artifactId + "/output.mp4";
        Path p = root.resolve("artifacts").resolve(objectKey);
        try {
            Files.createDirectories(p.getParent());
            Files.write(p, content);
            metadataStore.put(artifactId, metadata);
            return new StorageObjectRef(code(), "artifacts", objectKey);
        } catch (IOException e) {
            throw new IllegalStateException("failed to save artifact", e);
        }
    }

    public byte[] getArtifact(String artifactId) {
        Path p = root.resolve("artifacts").resolve(artifactId + "/output.mp4");
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read artifact", e);
        }
    }

    public Map<String, Object> getArtifactMetadata(String artifactId) {
        return metadataStore.getOrDefault(artifactId, Map.of());
    }
}
