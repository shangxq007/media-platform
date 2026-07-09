package com.example.platform.render.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Safe cleanup service for partial, abandoned, and orphan render outputs.
 * 
 * Targets worker temp/staging directories only.
 * Never deletes completed Product/Artifact outputs.
 * Never deletes RAW_MEDIA inputs.
 */
@Service
public class RenderWorkerOutputCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RenderWorkerOutputCleanupService.class);

    /**
     * Get cleanup summary without deleting anything.
     */
    public Map<String, Object> getCleanupSummary(Path tempRoot, Duration maxAge, int batchSize) {
        Instant cutoff = Instant.now().minus(maxAge);
        List<Path> candidates = new ArrayList<>();
        long bytesEligible = 0;

        if (Files.exists(tempRoot)) {
            try {
                candidates = scanCandidates(tempRoot, cutoff, batchSize);
                for (Path p : candidates) {
                    try {
                        bytesEligible += Files.size(p);
                    } catch (IOException e) { /* ignore */ }
                }
            } catch (IOException e) {
                log.warn("Failed to scan temp root: {}", e.getMessage());
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("enabled", true);
        summary.put("dryRun", true);
        summary.put("tempRoot", tempRoot.toString());
        summary.put("maxAge", maxAge.toString());
        summary.put("batchSize", batchSize);
        summary.put("candidatesScanned", candidates.size());
        summary.put("candidatesEligible", candidates.size());
        summary.put("bytesEligible", bytesEligible);
        summary.put("generatedAt", Instant.now().toString());
        return summary;
    }

    /**
     * Run cleanup.
     * 
     * @param tempRoot worker temp root directory
     * @param maxAge   max file age for cleanup
     * @param batchSize max files to delete per run
     * @param dryRun   if true, only count candidates
     * @return cleanup summary
     */
    public Map<String, Object> cleanup(Path tempRoot, Duration maxAge, int batchSize, boolean dryRun) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(maxAge);

        log.info("Output cleanup: tempRoot={} maxAge={} dryRun={} batchSize={}",
                tempRoot, maxAge, dryRun, batchSize);

        if (!Files.exists(tempRoot)) {
            log.info("Temp root does not exist: {}", tempRoot);
            return summary(now, tempRoot, maxAge, batchSize, true, 0, 0, 0, 0);
        }

        List<Path> candidates;
        try {
            candidates = scanCandidates(tempRoot, cutoff, batchSize);
        } catch (IOException e) {
            log.warn("Failed to scan candidates: {}", e.getMessage());
            return summary(now, tempRoot, maxAge, batchSize, dryRun, 0, 0, 0, 0);
        }
        int deleted = 0;
        long bytesDeleted = 0;

        if (!dryRun) {
            for (Path candidate : candidates) {
                try {
                    long size = Files.size(candidate);
                    Files.delete(candidate);
                    deleted++;
                    bytesDeleted += size;
                    log.debug("Deleted partial output: {}", candidate.getFileName());
                } catch (IOException e) {
                    log.warn("Failed to delete {}: {}", candidate.getFileName(), e.getMessage());
                }
            }
            // Clean empty directories
            cleanEmptyDirectories(tempRoot);
        }

        Map<String, Object> result = summary(now, tempRoot, maxAge, batchSize, dryRun,
                candidates.size(), deleted, 0, bytesDeleted);
        log.info("Output cleanup complete: candidates={} deleted={} dryRun={}",
                candidates.size(), deleted, dryRun);
        return result;
    }

    private List<Path> scanCandidates(Path tempRoot, Instant cutoff, int limit) throws IOException {
        List<Path> candidates = new ArrayList<>();
        Files.walkFileTree(tempRoot, EnumSet.noneOf(FileVisitOption.class), 5, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (candidates.size() >= limit) return FileVisitResult.TERMINATE;
                try {
                    // Only include files older than cutoff
                    if (attrs.lastModifiedTime().toInstant().isBefore(cutoff)) {
                        // Safety: never delete outside temp root
                        if (file.startsWith(tempRoot)) {
                            // Safety: skip symlinks
                            if (!Files.isSymbolicLink(file)) {
                                candidates.add(file);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Skipping file {}: {}", file.getFileName(), e.getMessage());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                log.debug("Cannot visit file {}: {}", file, e.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
        return candidates;
    }

    private void cleanEmptyDirectories(Path root) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (!dir.equals(root) && isDirectoryEmpty(dir)) {
                        Files.delete(dir);
                        log.debug("Deleted empty directory: {}", dir.getFileName());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.debug("Failed to clean empty directories: {}", e.getMessage());
        }
    }

    private boolean isDirectoryEmpty(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream.findFirst().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    private Map<String, Object> summary(Instant now, Path tempRoot, Duration maxAge, int batchSize,
                                         boolean dryRun, int scanned, int deleted, int skipped, long bytesDeleted) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("enabled", true);
        s.put("dryRun", dryRun);
        s.put("tempRoot", tempRoot.toString());
        s.put("maxAge", maxAge.toString());
        s.put("batchSize", batchSize);
        s.put("candidatesScanned", scanned);
        s.put("candidatesEligible", scanned);
        s.put("filesDeleted", deleted);
        s.put("bytesDeleted", bytesDeleted);
        s.put("skippedUnsafe", skipped);
        s.put("completedAt", now.toString());
        return s;
    }
}
