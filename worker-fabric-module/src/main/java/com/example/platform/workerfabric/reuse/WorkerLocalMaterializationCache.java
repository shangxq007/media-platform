package com.example.platform.workerfabric.reuse;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.shared.digest.ContentDigest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded rebuildable worker-local byte cache keyed only by immutable ContentDigest. */
@org.springframework.modulith.NamedInterface("runtime")
public final class WorkerLocalMaterializationCache {

    private final Path root;
    private final long capacityBytes;
    private final Object rootFileKey;
    private final Map<String, Object> digestLocks = new ConcurrentHashMap<>();

    public WorkerLocalMaterializationCache(Path root, long capacityBytes) {
        Objects.requireNonNull(root, "root");
        if (capacityBytes < 1) {
            throw new IllegalArgumentException("capacityBytes must be positive");
        }
        this.root = root.toAbsolutePath().normalize();
        this.capacityBytes = capacityBytes;
        try {
            rejectSymlinks(this.root);
            Files.createDirectories(this.root);
            this.rootFileKey = Objects.requireNonNull(Files.readAttributes(this.root, java.nio.file.attribute.BasicFileAttributes.class, java.nio.file.LinkOption.NOFOLLOW_LINKS).fileKey(), "cache directory identity required");
        } catch (IOException exception) {
            throw new IllegalArgumentException("cannot create materialization cache root", exception);
        }
    }

    public ArtifactMaterializationResult getOrMaterialize(
            ArtifactPin artifactPin,
            InputStreamSource remoteSource) throws IOException {
        Objects.requireNonNull(artifactPin, "artifactPin");
        Objects.requireNonNull(remoteSource, "remoteSource");
        String digest = artifactPin.contentDigest().canonicalValue();
        Object lock = digestLocks.computeIfAbsent(digest, ignored -> new Object());
        synchronized (lock) {
            try {
                Path target = target(artifactPin.contentDigest());
                validateCachePath(target);
                if (Files.isRegularFile(target) && hasExpectedDigest(target, artifactPin.contentDigest())) {
                    Files.setLastModifiedTime(target, FileTime.fromMillis(System.currentTimeMillis()));
                    return new ArtifactMaterializationResult(
                            new MaterializedArtifact(artifactPin, target, Files.size(target)),
                            MaterializationDisposition.LOCAL_CACHE_HIT);
                }
                boolean corruptLocalFile = Files.isRegularFile(target);
                Files.deleteIfExists(target);
                Files.createDirectories(target.getParent());
                Path temporary = Files.createTempFile(target.getParent(), ".materializing-", ".tmp");
                try {
                    long length;
                    try (InputStream input = remoteSource.open()) {
                        if (input == null) {
                            throw new ArtifactMaterializationException("storage provider returned no bytes");
                        }
                        length = 0;
                        try (var output = Files.newOutputStream(temporary, java.nio.file.StandardOpenOption.WRITE,
                                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                            byte[] buffer = new byte[64 * 1024];
                            int read;
                            while ((read = input.read(buffer)) != -1) {
                                if (read > capacityBytes - length) throw new ArtifactMaterializationException(
                                        "Artifact exceeds worker-local materialization cache capacity");
                                output.write(buffer, 0, read);
                                length += read;
                            }
                        }
                    }
                    if (length > capacityBytes) {
                        throw new ArtifactMaterializationException(
                                "Artifact exceeds worker-local materialization cache capacity");
                    }
                    if (!hasExpectedDigest(temporary, artifactPin.contentDigest())) {
                        throw new ArtifactMaterializationException(
                                "materialized bytes do not match Artifact content digest");
                    }
                    validateCachePath(target);
                    atomicPublish(temporary, target);
                    evictToBound(target);
                    return new ArtifactMaterializationResult(
                            new MaterializedArtifact(artifactPin, target, length),
                            corruptLocalFile
                                    ? MaterializationDisposition.CORRUPTION_RECOVERED
                                    : MaterializationDisposition.STORAGE_MATERIALIZED);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            } finally {
                digestLocks.remove(digest, lock);
            }
        }
    }

    public long usedBytes() throws IOException {
        if (!Files.exists(root)) {
            return 0;
        }
        try (var paths = Files.walk(root)) {
            return paths.peek(path -> {
                        if (Files.isSymbolicLink(path)) throw new CacheWalkFailure(new IOException("cache symlink refused"));
                    }).filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().endsWith(".tmp"))
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException exception) {
                            throw new CacheWalkFailure(exception);
                        }
                    })
                    .sum();
        } catch (CacheWalkFailure failure) {
            throw failure.ioException;
        }
    }

    private static void rejectSymlinks(Path path) throws IOException {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) throw new IOException("cache symlink refused");
        }
    }

    private void validateCachePath(Path path) throws IOException {
        rejectSymlinks(path);
        var attributes = Files.readAttributes(root, java.nio.file.attribute.BasicFileAttributes.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
        if (!path.startsWith(root) || !attributes.isDirectory() || !rootFileKey.equals(attributes.fileKey())) {
            throw new IOException("cache root identity changed");
        }
    }

    private Path target(ContentDigest digest) {
        String value = digest.canonicalValue();
        Path path = root.resolve(digest.algorithm().name().toLowerCase())
                .resolve(value.substring(0, 2))
                .resolve(value)
                .normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("ContentDigest resolved outside cache root");
        }
        return path;
    }

    private void evictToBound(Path protectedPath) throws IOException {
        long used = usedBytes();
        if (used <= capacityBytes) {
            return;
        }
        List<Path> candidates;
        try (var paths = Files.walk(root)) {
            candidates = paths.filter(Files::isRegularFile)
                    .filter(path -> !path.equals(protectedPath))
                    .filter(path -> !path.getFileName().toString().endsWith(".tmp"))
                    .sorted(Comparator.comparing(path -> {
                        try {
                            return Files.getLastModifiedTime(path);
                        } catch (IOException exception) {
                            return FileTime.fromMillis(0);
                        }
                    }))
                    .toList();
        }
        for (Path candidate : candidates) {
            if (used <= capacityBytes) {
                break;
            }
            validateCachePath(candidate);
            long size = Files.size(candidate);
            Files.deleteIfExists(candidate);
            used -= size;
        }
    }

    private static boolean hasExpectedDigest(Path path, ContentDigest expected) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
        try (InputStream input = Files.newInputStream(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return expected.matches(ContentDigest.sha256(HexFormat.of().formatHex(digest.digest())));
    }

    private static void atomicPublish(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @FunctionalInterface
    public interface InputStreamSource {
        InputStream open() throws IOException;
    }

    private static final class CacheWalkFailure extends RuntimeException {
        private final IOException ioException;

        private CacheWalkFailure(IOException ioException) {
            this.ioException = ioException;
        }
    }
}
