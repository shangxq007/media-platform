package com.example.platform.storage.infrastructure;

import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.PutObjectCommand;
import com.example.platform.storage.domain.StorageObjectRef;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
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
    public String presign(String objectKey) {
        return root.resolve(objectKey).toUri().toString();
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
