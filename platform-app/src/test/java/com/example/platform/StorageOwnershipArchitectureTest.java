package com.example.platform;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PMPR-ST1 architecture guards.
 *
 * STORAGE_SPI_BELONGS_TO_STORAGE_AUTHORITY, NOT TO RENDER:
 * canonical storage contracts (StorageProvider SPI, ContentDigest,
 * StorageObjectId, StorageReplicaId, StorageProviderId) must live in
 * storage-module, never under render authority.
 */
class StorageOwnershipArchitectureTest {

    private static final List<String> CANONICAL_CONTRACTS = List.of(
            "ContentDigest.java",
            "StorageObjectId.java",
            "StorageReplicaId.java",
            "StorageProviderId.java",
            "StorageProvider.java"
    );

    private static Path repoRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.isRegularFile(p.resolve("settings.gradle.kts"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("repo root not found (settings.gradle.kts)");
        }
        return p;
    }

    private static List<String> filesUnder(String module, String subPath) {
        Path dir = repoRoot().resolve(module).resolve(subPath);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .map(Path::toString)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void renderMustNotOwnCanonicalStorageContracts() {
        // canonical contracts originally lived at render/domain/storage top-level,
        // render/domain/storage/digest, and render/domain/storage/identity.
        // provider/StorageProvider (full cluster interface) is a render-internal
        // implementation detail and is NOT a canonical contract.
        List<String> renderOwned = List.of(
                        filesUnder("render-module", "src/main/java/com/example/platform/render/domain/storage"),
                        filesUnder("render-module", "src/main/java/com/example/platform/render/domain/storage/digest"),
                        filesUnder("render-module", "src/main/java/com/example/platform/render/domain/storage/identity"))
                .stream()
                .flatMap(List::stream)
                .filter(p -> !p.contains("/provider/"))
                .filter(p -> CANONICAL_CONTRACTS.contains(Path.of(p).getFileName().toString()))
                .toList();
        assertTrue(renderOwned.isEmpty(),
                "Canonical storage contracts must NOT live under render authority, found: " + renderOwned);
    }

    @Test
    void storageModuleMustOwnCanonicalStorageContracts() {
        List<String> storageOwned = filesUnder("storage-module", "src/main/java/com/example/platform/storage/contract");
        List<String> found = CANONICAL_CONTRACTS.stream()
                .filter(name -> storageOwned.stream().anyMatch(p -> p.endsWith(name)))
                .toList();
        assertTrue(found.size() == CANONICAL_CONTRACTS.size(),
                "storage-module must own all canonical contracts, found: " + found);
    }
}
