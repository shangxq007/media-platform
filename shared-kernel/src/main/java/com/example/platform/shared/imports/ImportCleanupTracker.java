package com.example.platform.shared.imports;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tracks resources created during a project import for rollback/cleanup.
 *
 * <p>Registered artifacts, stored blobs, and downloaded temp files are tracked.
 * On failure, the tracker performs compensating cleanup in reverse order.
 *
 * <p>This class is not thread-safe — each import should use its own instance.
 */
public final class ImportCleanupTracker {

    private static final Logger log = LoggerFactory.getLogger(ImportCleanupTracker.class);

    private final List<String> registeredArtifactIds = new ArrayList<>();
    private final List<String> storedBlobUris = new ArrayList<>();
    private final List<Path> tempFiles = new ArrayList<>();
    private final List<String> cleanupWarnings = new ArrayList<>();
    private boolean committed = false;

    public void trackRegisteredArtifact(String artifactId) {
        if (artifactId != null) {
            registeredArtifactIds.add(artifactId);
        }
    }

    public void trackStoredBlob(String storageUri) {
        if (storageUri != null) {
            storedBlobUris.add(storageUri);
        }
    }

    public void trackTempFile(Path tempFile) {
        if (tempFile != null) {
            tempFiles.add(tempFile);
        }
    }

    /**
     * Mark the import as successfully committed. No rollback will be performed.
     * Temp files are still cleaned up.
     */
    public void commit() {
        this.committed = true;
        cleanupTempFiles();
    }

    /**
     * Perform compensating cleanup after a failure.
     * Deletes stored blobs, tombstones registered artifacts (reverse order), and deletes temp files.
     *
     * @param blobDeleter       function to delete a blob by storageUri
     * @param artifactTombstoner function to tombstone an artifact by id
     * @return list of cleanup warnings (empty if all succeeded)
     */
    public List<String> rollback(java.util.function.Consumer<String> blobDeleter,
                                  java.util.function.Consumer<String> artifactTombstoner) {
        if (committed) {
            cleanupTempFiles();
            return List.of();
        }

        // 1. Delete stored blobs (reverse order)
        List<String> blobUris = new ArrayList<>(storedBlobUris);
        Collections.reverse(blobUris);
        for (String uri : blobUris) {
            try {
                blobDeleter.accept(uri);
                log.info("Deleted blob for rollback: {}", uri);
            } catch (Exception e) {
                String msg = "Failed to delete blob " + uri + ": " + e.getMessage();
                log.warn(msg);
                cleanupWarnings.add(msg);
            }
        }

        // 2. Tombstone registered artifacts (reverse order)
        List<String> ids = new ArrayList<>(registeredArtifactIds);
        Collections.reverse(ids);
        for (String artifactId : ids) {
            try {
                artifactTombstoner.accept(artifactId);
                log.info("Tombstoned artifact for rollback: {}", artifactId);
            } catch (Exception e) {
                String msg = "Failed to tombstone artifact " + artifactId + ": " + e.getMessage();
                log.warn(msg);
                cleanupWarnings.add(msg);
            }
        }

        // 3. Delete temp files
        cleanupTempFiles();

        return List.copyOf(cleanupWarnings);
    }

    /**
     * Cleanup without rollback (for pre-registration failures).
     */
    public void cleanupOnly() {
        cleanupTempFiles();
    }

    public List<String> getRegisteredArtifactIds() {
        return List.copyOf(registeredArtifactIds);
    }

    public List<String> getStoredBlobUris() {
        return List.copyOf(storedBlobUris);
    }

    public boolean hasRegisteredArtifacts() {
        return !registeredArtifactIds.isEmpty();
    }

    public boolean hasStoredBlobs() {
        return !storedBlobUris.isEmpty();
    }

    public List<String> getCleanupWarnings() {
        return List.copyOf(cleanupWarnings);
    }

    private void cleanupTempFiles() {
        for (Path tempFile : tempFiles) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                log.warn("Failed to cleanup temp file {}: {}", tempFile, e.getMessage());
            }
        }
        tempFiles.clear();
    }
}
