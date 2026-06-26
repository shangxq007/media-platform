package com.example.platform.render.app.storage;

import com.example.platform.render.domain.storage.StorageReference;
import com.example.platform.render.infrastructure.storage.StorageReferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.security.*;
import java.util.Optional;

@Service
public class StorageRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(StorageRuntimeService.class);
    private final StorageReferenceRepository repo;

    public StorageRuntimeService(StorageReferenceRepository repo) { this.repo = repo; }

    @Transactional
    public StorageReference register(StorageReference ref) {
        var saved = repo.save(ref);
        log.info("Storage reference registered: id={} path={}", saved.storageReferenceId(), saved.absolutePath());
        return saved;
    }

    public String materialize(String storageReferenceId) {
        var ref = repo.findById(storageReferenceId)
                .orElseThrow(() -> new IllegalArgumentException("Storage not found: " + storageReferenceId));
        String path = ref.absolutePath();
        var file = new java.io.File(path);
        if (!file.exists()) throw new IllegalStateException("File not materialized: " + path);
        return path;
    }

    public boolean verifyChecksum(String storageReferenceId) {
        var ref = repo.findById(storageReferenceId).orElseThrow();
        try {
            byte[] fileBytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(ref.absolutePath()));
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(fileBytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString().equalsIgnoreCase(ref.checksum());
        } catch (Exception e) { return false; }
    }

    public Optional<StorageReference> find(String id) { return repo.findById(id); }
    public boolean exists(String id) { return repo.exists(id); }
    @Transactional public void delete(String id) { repo.delete(id); }
}
