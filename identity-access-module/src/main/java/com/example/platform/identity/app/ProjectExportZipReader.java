package com.example.platform.identity.app;

import com.example.platform.identity.api.dto.*;
import com.example.platform.shared.io.ChecksumFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads and validates a project-export-v1 zip archive.
 *
 * <p>Security measures:
 * <ul>
 *   <li>Entry name allowlist (prevents zip slip)</li>
 *   <li>Zip bomb protection: entry count limit, total size limit, per-entry size limit</li>
 *   <li>No path traversal: rejects {@code ..}, absolute paths, backslash traversal</li>
 *   <li>Checksum validation against sha256sums.txt</li>
 *   <li>Only reads in-memory (no temp files on disk)</li>
 * </ul>
 */
@Service
public class ProjectExportZipReader {

    private static final Logger log = LoggerFactory.getLogger(ProjectExportZipReader.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // Zip bomb protection limits
    private static final long MAX_ZIP_SIZE_BYTES = 50L * 1024 * 1024; // 50 MB compressed
    private static final long MAX_UNCOMPRESSED_SIZE_BYTES = 200L * 1024 * 1024; // 200 MB uncompressed
    private static final int MAX_ENTRY_COUNT = 100;
    private static final long MAX_ENTRY_SIZE_BYTES = 50L * 1024 * 1024; // 50 MB per entry

    // Required entries that must be present
    private static final Set<String> REQUIRED_ENTRIES = Set.of(
            "manifest.json", "project.json", "assets.json"
    );

    // Allowed entry names (without root prefix)
    private static final String CANONICAL_ROOT = "project-export-v1";
    private static final Set<String> ALLOWED_ENTRIES = Set.of(
            "manifest.json",
            "project.json",
            "assets.json",
            "timeline/timeline.json",
            "render/render-plan.json",
            "render/spatial-plan.json",
            "render/export-profiles.json",
            "effects/effect-taxonomy.json",
            "effects/applied-effects.json",
            "outputs/outputs-manifest.json",
            "audit/audit-summary.json",
            "checksums/sha256sums.txt",
            "README.md"
    );

    /**
     * Result of reading and validating a zip archive.
     */
    public record ZipReadResult(
            ProjectExportPackageDto exportPackage,
            List<String> warnings,
            List<String> errors,
            boolean valid
    ) {}

    /**
     * Read and validate a project-export-v1 zip archive.
     *
     * @param zipInputStream the zip input stream
     * @param zipSize        compressed size in bytes (for size limit check)
     * @return validation result with parsed package or errors
     */
    public ZipReadResult readArchive(InputStream zipInputStream, long zipSize) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Check compressed size
        if (zipSize > MAX_ZIP_SIZE_BYTES) {
            errors.add("Zip file exceeds maximum size of " + MAX_ZIP_SIZE_BYTES / (1024 * 1024) + " MB");
            return new ZipReadResult(null, warnings, errors, false);
        }

        Map<String, String> entryContents = new LinkedHashMap<>();
        long totalUncompressedSize = 0;
        int entryCount = 0;
        String detectedRootPrefix = null;

        try (ZipInputStream zis = new ZipInputStream(zipInputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;

                // Entry count limit
                if (entryCount > MAX_ENTRY_COUNT) {
                    errors.add("Zip contains more than " + MAX_ENTRY_COUNT + " entries (zip bomb protection)");
                    return new ZipReadResult(null, warnings, errors, false);
                }

                String rawEntryName = entry.getName();

                // Zip slip and path traversal protection
                String pathError = validateEntryName(rawEntryName);
                if (pathError != null) {
                    errors.add(pathError);
                    zis.closeEntry();
                    continue;
                }

                // Normalize: strip canonical root prefix
                String entryName = stripRootPrefix(rawEntryName, warnings);
                if (entryName == null) {
                    errors.add("Zip entry has unknown root prefix: " + rawEntryName);
                    zis.closeEntry();
                    continue;
                }

                // Entry allowlist
                if (!ALLOWED_ENTRIES.contains(entryName)) {
                    errors.add("Unknown zip entry not in allowlist: " + entryName);
                    zis.closeEntry();
                    continue;
                }

                // Read entry content with size limit
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int bytesRead;
                long entrySize = 0;
                while ((bytesRead = zis.read(buf)) != -1) {
                    entrySize += bytesRead;
                    totalUncompressedSize += bytesRead;

                    // Per-entry size limit
                    if (entrySize > MAX_ENTRY_SIZE_BYTES) {
                        errors.add("Zip entry '" + entryName + "' exceeds per-entry size limit of "
                                + MAX_ENTRY_SIZE_BYTES / (1024 * 1024) + " MB");
                        zis.closeEntry();
                        break;
                    }

                    // Total uncompressed size limit
                    if (totalUncompressedSize > MAX_UNCOMPRESSED_SIZE_BYTES) {
                        errors.add("Total uncompressed size exceeds " +
                                MAX_UNCOMPRESSED_SIZE_BYTES / (1024 * 1024) + " MB (zip bomb protection)");
                        zis.closeEntry();
                        break;
                    }

                    buffer.write(buf, 0, bytesRead);
                }

                entryContents.put(entryName, buffer.toString(StandardCharsets.UTF_8));
                zis.closeEntry();
            }
        } catch (IOException e) {
            errors.add("Failed to read zip: " + e.getMessage());
            return new ZipReadResult(null, warnings, errors, false);
        }

        // Check required entries
        for (String required : REQUIRED_ENTRIES) {
            if (!entryContents.containsKey(required)) {
                errors.add("Missing required entry: " + required);
            }
        }

        if (!errors.isEmpty()) {
            return new ZipReadResult(null, warnings, errors, false);
        }

        // Validate checksums if present
        String checksumsContent = entryContents.get("checksums/sha256sums.txt");
        if (checksumsContent != null) {
            String checksumError = validateChecksums(entryContents, checksumsContent);
            if (checksumError != null) {
                errors.add(checksumError);
                return new ZipReadResult(null, warnings, errors, false);
            }
        } else {
            warnings.add("Missing checksums/sha256sums.txt — checksums not validated");
        }

        // Parse JSON entries
        try {
            JsonNode manifestNode = MAPPER.readTree(entryContents.get("manifest.json"));
            JsonNode projectNode = MAPPER.readTree(entryContents.get("project.json"));
            JsonNode assetsNode = MAPPER.readTree(entryContents.get("assets.json"));

            // Extract schema version and export mode
            String schemaVersion = manifestNode.path("schemaVersion").asText(null);
            String exportMode = manifestNode.path("exportMode").asText(null);

            // Build export package DTO
            ProjectExportManifestDto manifest = MAPPER.treeToValue(manifestNode, ProjectExportManifestDto.class);
            ProjectExportProjectDto project = MAPPER.treeToValue(projectNode, ProjectExportProjectDto.class);
            ProjectExportAssetsDto assets = MAPPER.treeToValue(assetsNode, ProjectExportAssetsDto.class);

            // Optional entries
            ProjectExportTimelineDto timeline = null;
            if (entryContents.containsKey("timeline/timeline.json")) {
                timeline = MAPPER.readValue(entryContents.get("timeline/timeline.json"),
                        ProjectExportTimelineDto.class);
            }

            ProjectExportRenderDto render = null;
            if (entryContents.containsKey("render/render-plan.json")) {
                render = MAPPER.readValue(entryContents.get("render/render-plan.json"),
                        ProjectExportRenderDto.class);
            }

            ProjectExportPackageDto exportPackage = new ProjectExportPackageDto(
                    schemaVersion, exportMode, manifest, project, assets, timeline, render);

            return new ZipReadResult(exportPackage, warnings, errors, true);

        } catch (Exception e) {
            errors.add("Failed to parse zip contents: " + e.getMessage());
            return new ZipReadResult(null, warnings, errors, false);
        }
    }

    /**
     * Strip the canonical root prefix from an entry name.
     * Returns the normalized entry name, or null if the prefix is unknown.
     */
    private String stripRootPrefix(String rawEntryName, List<String> warnings) {
        // Already normalized (no prefix)
        if (ALLOWED_ENTRIES.contains(rawEntryName)) {
            return rawEntryName;
        }

        // Check for canonical root prefix
        String prefix = CANONICAL_ROOT + "/";
        if (rawEntryName.startsWith(prefix)) {
            String stripped = rawEntryName.substring(prefix.length());
            if (ALLOWED_ENTRIES.contains(stripped)) {
                return stripped;
            }
        }

        // Unknown entry
        return null;
    }

    /**
     * Validate entry name — prevent zip slip and path traversal.
     */
    private String validateEntryName(String entryName) {
        if (entryName == null || entryName.isBlank()) {
            return "Zip entry name cannot be null or blank";
        }
        if (entryName.contains("..")) {
            return "Zip entry name cannot contain '..': " + entryName;
        }
        if (entryName.startsWith("/") || entryName.startsWith("\\")) {
            return "Zip entry name cannot start with / or \\: " + entryName;
        }
        if (entryName.contains("\\")) {
            return "Zip entry name cannot contain backslash: " + entryName;
        }
        return null;
    }

    /**
     * Validate checksums from sha256sums.txt against entry contents.
     */
    private String validateChecksums(Map<String, String> entryContents, String checksumsContent) {
        // Parse checksums, normalizing paths by stripping root prefix
        Map<String, String> expectedChecksums = new LinkedHashMap<>();
        String rootPrefix = CANONICAL_ROOT + "/";
        for (String line : checksumsContent.split("\n")) {
            line = line.trim();
            if (line.startsWith("#") || line.isEmpty()) continue;
            String[] parts = line.split("\\s+", 2);
            if (parts.length == 2) {
                String path = parts[1].trim();
                // Normalize: strip root prefix
                if (path.startsWith(rootPrefix)) {
                    path = path.substring(rootPrefix.length());
                }
                expectedChecksums.put(path, parts[0].trim());
            }
        }

        // sha256sums.txt should not reference itself
        if (expectedChecksums.containsKey("checksums/sha256sums.txt")) {
            return "sha256sums.txt should not reference itself";
        }

        // Verify each entry's checksum
        for (Map.Entry<String, String> entry : entryContents.entrySet()) {
            String entryName = entry.getKey();
            if ("checksums/sha256sums.txt".equals(entryName)) continue;

            String expected = expectedChecksums.get(entryName);
            if (expected == null) {
                // Missing checksum is a warning, not an error
                continue;
            }

            String actual = sha256hex(entry.getValue().getBytes(StandardCharsets.UTF_8));
            if (!expected.equals(actual)) {
                return "Checksum mismatch for " + entryName + ": expected " + expected + ", got " + actual;
            }
        }

        return null; // All checksums valid
    }

    private static String sha256hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ByteArrayOutputStream for reading zip entries
    private static class ByteArrayOutputStream extends java.io.ByteArrayOutputStream {
        ByteArrayOutputStream() { super(); }
    }
}
