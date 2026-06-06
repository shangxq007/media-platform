package com.example.platform.identity.app;

import com.example.platform.identity.api.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ProjectExportZipReaderTest {

    private ProjectExportZipReader reader;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        reader = new ProjectExportZipReader();
    }

    // ─── Helper: build valid zip ───

    private byte[] buildValidMetadataOnlyZip() throws Exception {
        Instant now = Instant.now();
        Map<String, String> entries = new LinkedHashMap<>();

        // manifest.json
        ProjectExportSecurityDto security = new ProjectExportSecurityDto(
                false, false, false, false, true, true, true);
        ProjectExportManifestDto manifest = new ProjectExportManifestDto(
                "project-export-v1", "project-export-v1", "exp-1",
                "metadata_only", now, "user-1",
                Map.of(), security, new ProjectExportManifestAssetsDto("metadata_only", 0, 0),
                Map.of("algorithm", "sha256"));
        entries.put("manifest.json", mapper.writeValueAsString(manifest));

        // project.json
        ProjectExportProjectDto project = new ProjectExportProjectDto(
                "prj-1", "tenant-1", "Test Project", "desc", now, now, "ACTIVE");
        entries.put("project.json", mapper.writeValueAsString(project));

        // assets.json
        ProjectExportAssetsDto assets = new ProjectExportAssetsDto(
                "project-export-v1", "metadata_only", List.of(), null);
        entries.put("assets.json", mapper.writeValueAsString(assets));

        // timeline/timeline.json
        ProjectExportTimelineDto timeline = new ProjectExportTimelineDto(
                "project-export-v1", List.of(), 0.0);
        entries.put("timeline/timeline.json", mapper.writeValueAsString(timeline));

        // render/render-plan.json
        ProjectExportRenderDto render = new ProjectExportRenderDto(
                "project-export-v1", Map.of(), Map.of(), "v1");
        entries.put("render/render-plan.json", mapper.writeValueAsString(render));

        // effects
        entries.put("effects/effect-taxonomy.json", mapper.writeValueAsString(Map.of("schemaVersion", "project-export-v1")));
        entries.put("effects/applied-effects.json", mapper.writeValueAsString(Map.of("schemaVersion", "project-export-v1")));

        // outputs
        ProjectExportOutputsDto outputs = new ProjectExportOutputsDto("project-export-v1", 0, List.of());
        entries.put("outputs/outputs-manifest.json", mapper.writeValueAsString(outputs));

        // audit
        ProjectExportAuditDto audit = new ProjectExportAuditDto(
                "project-export-v1", "aud-1", now, "user-1", "PROJECT_IMPORT_PREVIEW");
        entries.put("audit/audit-summary.json", mapper.writeValueAsString(audit));

        // checksums
        StringBuilder checksums = new StringBuilder();
        checksums.append("# SHA-256 checksums\n# Format: <sha256> <filename>\n\n");
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String hash = sha256hex(entry.getValue().getBytes(StandardCharsets.UTF_8));
            checksums.append(hash).append("  ").append(entry.getKey()).append("\n");
        }
        entries.put("checksums/sha256sums.txt", checksums.toString());

        // README.md
        entries.put("README.md", "# Test Export\n");

        return buildZipFromEntries(entries);
    }

    private byte[] buildValidLinkedAssetsZip() throws Exception {
        Instant now = Instant.now();
        Map<String, String> entries = new LinkedHashMap<>();

        // manifest.json
        ProjectExportSecurityDto security = new ProjectExportSecurityDto(
                true, false, false, false, true, true, true);
        ProjectExportManifestDto manifest = new ProjectExportManifestDto(
                "project-export-v1", "project-export-v1", "exp-2",
                "linked_assets", now, "user-1",
                Map.of(), security, new ProjectExportManifestAssetsDto("linked_assets", 1, 0),
                Map.of("algorithm", "sha256"));
        entries.put("manifest.json", mapper.writeValueAsString(manifest));

        // project.json
        ProjectExportProjectDto project = new ProjectExportProjectDto(
                "prj-2", "tenant-1", "Linked Project", "desc", now, now, "ACTIVE");
        entries.put("project.json", mapper.writeValueAsString(project));

        // assets.json with signed URLs
        ProjectExportAssetDto asset = new ProjectExportAssetDto(
                "asset-1", "video.mp4", "video", "video/mp4",
                1234567L, "sha256:abc123", 5.0, 1920, 1080,
                null, "https://signed.example.com/video?token=abc123");
        ProjectExportAssetsDto assets = new ProjectExportAssetsDto(
                "project-export-v1", "linked_assets", List.of(asset), null);
        entries.put("assets.json", mapper.writeValueAsString(assets));

        // Optional entries
        entries.put("timeline/timeline.json", mapper.writeValueAsString(
                new ProjectExportTimelineDto("project-export-v1", List.of(), 0.0)));
        entries.put("render/render-plan.json", mapper.writeValueAsString(
                new ProjectExportRenderDto("project-export-v1", Map.of(), Map.of(), "v1")));
        entries.put("effects/effect-taxonomy.json", mapper.writeValueAsString(Map.of("schemaVersion", "project-export-v1")));
        entries.put("effects/applied-effects.json", mapper.writeValueAsString(Map.of("schemaVersion", "project-export-v1")));
        entries.put("outputs/outputs-manifest.json", mapper.writeValueAsString(
                new ProjectExportOutputsDto("project-export-v1", 0, List.of())));
        entries.put("audit/audit-summary.json", mapper.writeValueAsString(
                new ProjectExportAuditDto("project-export-v1", "aud-2", now, "user-1", "PROJECT_IMPORT_PREVIEW")));

        // checksums
        StringBuilder checksums = new StringBuilder();
        checksums.append("# SHA-256 checksums\n\n");
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String hash = sha256hex(entry.getValue().getBytes(StandardCharsets.UTF_8));
            checksums.append(hash).append("  ").append(entry.getKey()).append("\n");
        }
        entries.put("checksums/sha256sums.txt", checksums.toString());
        entries.put("README.md", "# Test Export\n");

        return buildZipFromEntries(entries);
    }

    private byte[] buildZipFromEntries(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ─── Tests ───

    @Test
    void readArchiveShouldAcceptValidMetadataOnlyZip() throws Exception {
        byte[] zip = buildValidMetadataOnlyZip();
        ProjectExportZipReader.ZipReadResult result = reader.readArchive(
                new ByteArrayInputStream(zip), zip.length);

        assertTrue(result.valid(), "Should be valid: " + result.errors());
        assertNotNull(result.exportPackage());
        assertEquals("project-export-v1", result.exportPackage().schemaVersion());
        assertEquals("metadata_only", result.exportPackage().exportMode());
        assertNotNull(result.exportPackage().project());
        assertEquals("prj-1", result.exportPackage().project().projectId());
        assertTrue(result.warnings().isEmpty(), "Should have no warnings: " + result.warnings());
        assertTrue(result.errors().isEmpty(), "Should have no errors: " + result.errors());
    }

    @Test
    void readArchiveShouldAcceptValidLinkedAssetsZip() throws Exception {
        byte[] zip = buildValidLinkedAssetsZip();
        ProjectExportZipReader.ZipReadResult result = reader.readArchive(
                new ByteArrayInputStream(zip), zip.length);

        assertTrue(result.valid(), "Should be valid: " + result.errors());
        assertEquals("linked_assets", result.exportPackage().exportMode());
        assertNotNull(result.exportPackage().assets());
        assertEquals(1, result.exportPackage().assets().assets().size());
        assertEquals("asset-1", result.exportPackage().assets().assets().get(0).assetId());
    }

    @Test
    void readArchiveShouldRejectZipSlipEntry() throws Exception {
        // Build a zip with a path traversal entry
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("../../../etc/passwd"));
            zos.write("evil".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        byte[] zip = baos.toByteArray();

        ProjectExportZipReader.ZipReadResult result = reader.readArchive(
                new ByteArrayInputStream(zip), zip.length);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("path traversal") || e.contains("'..")));
    }

    @Test
    void readArchiveShouldRejectAbsoluteEntry() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("/etc/passwd"));
            zos.write("evil".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        byte[] zip = baos.toByteArray();

        ProjectExportZipReader.ZipReadResult result = reader.readArchive(
                new ByteArrayInputStream(zip), zip.length);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("cannot start with")));
    }

    @Test
    void readArchiveShouldRejectUnknownEntry() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("malicious.exe"));
            zos.write("evil".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        byte[] zip = baos.toByteArray();

        ProjectExportZipReader.ZipReadResult result = reader.readArchive(
                new ByteArrayInputStream(zip), zip.length);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Unknown zip entry") || e.contains("unknown root prefix")),
                "Should reject unknown entry, got errors: " + result.errors());
    }

    @Test
    void readArchiveShouldRejectChecksumMismatch() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("manifest.json", "{}");
        entries.put("project.json", "{}");
        entries.put("assets.json", "{}");

        // Wrong checksums
        StringBuilder checksums = new StringBuilder();
        checksums.append("# SHA-256 checksums\n\n");
        checksums.append("0000000000000000000000000000000000000000000000000000000000000000  manifest.json\n");
        checksums.append("0000000000000000000000000000000000000000000000000000000000000000  project.json\n");
        checksums.append("0000000000000000000000000000000000000000000000000000000000000000  assets.json\n");
        entries.put("checksums/sha256sums.txt", checksums.toString());

        byte[] zip = buildZipFromEntries(entries);
        ProjectExportZipReader.ZipReadResult result = reader.readArchive(
                new ByteArrayInputStream(zip), zip.length);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Checksum mismatch")));
    }

    @Test
    void readArchiveShouldRejectMissingManifest() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("project.json", "{}");
        entries.put("assets.json", "{}");
        entries.put("README.md", "# Test\n");

        byte[] zip = buildZipFromEntries(entries);
        ProjectExportZipReader.ZipReadResult result = reader.readArchive(
                new ByteArrayInputStream(zip), zip.length);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Missing required entry: manifest.json")));
    }

    @Test
    void readArchiveShouldRejectSelfReferencingChecksumFile() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("manifest.json", "{}");
        entries.put("project.json", "{}");
        entries.put("assets.json", "{}");

        // Self-referencing checksums
        StringBuilder checksums = new StringBuilder();
        checksums.append("# SHA-256 checksums\n\n");
        checksums.append("0000000000000000000000000000000000000000000000000000000000000000  checksums/sha256sums.txt\n");
        entries.put("checksums/sha256sums.txt", checksums.toString());

        byte[] zip = buildZipFromEntries(entries);
        ProjectExportZipReader.ZipReadResult result = reader.readArchive(
                new ByteArrayInputStream(zip), zip.length);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("should not reference itself")));
    }

    @Test
    void readArchiveShouldRespectSizeLimit() throws Exception {
        // Build a tiny valid zip but report a huge compressed size
        byte[] zip = buildValidMetadataOnlyZip();
        ProjectExportZipReader.ZipReadResult result = reader.readArchive(
                new ByteArrayInputStream(zip), 100L * 1024 * 1024); // 100 MB reported

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("maximum size")));
    }

    @Test
    void readArchiveShouldNotDownloadSignedUrls() throws Exception {
        byte[] zip = buildValidLinkedAssetsZip();
        ProjectExportZipReader.ZipReadResult result = reader.readArchive(
                new ByteArrayInputStream(zip), zip.length);

        assertTrue(result.valid());
        // The signed URL should be in the parsed assets, but NOT downloaded
        ProjectExportAssetDto asset = result.exportPackage().assets().assets().get(0);
        assertNotNull(asset.downloadUrl());
        assertTrue(asset.downloadUrl().contains("signed.example.com"));
        // storageRef should be null
        assertNull(asset.storageRef());
    }

    @Test
    void readArchiveShouldHandleEmptyZip() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("README.md"));
            zos.write("# Empty\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        byte[] zip = baos.toByteArray();

        ProjectExportZipReader.ZipReadResult result = reader.readArchive(
                new ByteArrayInputStream(zip), zip.length);

        assertFalse(result.valid());
        // Should have errors for missing manifest, project, assets
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Missing required entry")));
    }

    @Test
    void readArchiveShouldWarnWhenChecksumsMissing() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("manifest.json", "{}");
        entries.put("project.json", "{}");
        entries.put("assets.json", "{}");
        entries.put("README.md", "# Test\n");

        byte[] zip = buildZipFromEntries(entries);
        ProjectExportZipReader.ZipReadResult result = reader.readArchive(
                new ByteArrayInputStream(zip), zip.length);

        // Should still be valid (checksums are optional), but have a warning
        // Note: without proper JSON, it won't parse correctly, but the warning should be present
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("checksums")),
                "Should warn about missing checksums");
    }

    // ─── Root prefix normalization ───

    @Test
    void readerShouldNormalizeProjectExportV1RootPrefix() throws Exception {
        // Build zip with project-export-v1/ prefix
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("manifest.json", "{}");
        entries.put("project.json", "{}");
        entries.put("assets.json", "{}");
        entries.put("README.md", "# Test\n");

        // Build zip with root prefix
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry("project-export-v1/" + entry.getKey()));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        byte[] zip = baos.toByteArray();

        ProjectExportZipReader.ZipReadResult result = reader.readArchive(
                new ByteArrayInputStream(zip), zip.length);

        // Should not have "unknown root prefix" errors
        assertFalse(result.errors().stream().anyMatch(e -> e.contains("unknown root prefix")),
                "Should accept project-export-v1/ prefix, got errors: " + result.errors());
    }

    @Test
    void readerShouldRejectUnknownRootPrefix() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("evil/manifest.json"));
            zos.write("{}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        byte[] zip = baos.toByteArray();

        ProjectExportZipReader.ZipReadResult result = reader.readArchive(
                new ByteArrayInputStream(zip), zip.length);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("unknown root prefix")),
                "Should reject evil/ prefix, got errors: " + result.errors());
    }

    @Test
    void readerShouldRejectRootPrefixTraversal() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("project-export-v1/../evil/manifest.json"));
            zos.write("{}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        byte[] zip = baos.toByteArray();

        ProjectExportZipReader.ZipReadResult result = reader.readArchive(
                new ByteArrayInputStream(zip), zip.length);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("'..'")),
                "Should reject path traversal, got errors: " + result.errors());
    }

    @Test
    void checksumPathsShouldMatchNormalizedEntries() throws Exception {
        // Build a valid zip with root prefix and verify checksum validation works
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String manifestContent = "{}";
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("project-export-v1/manifest.json"));
            zos.write(manifestContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("project-export-v1/project.json"));
            zos.write("{}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("project-export-v1/assets.json"));
            zos.write("{}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Build checksums with canonical paths
            String manifestHash = sha256hex(manifestContent.getBytes(StandardCharsets.UTF_8));
            String checksums = "# checksums\n\n" +
                    manifestHash + "  project-export-v1/manifest.json\n";
            zos.putNextEntry(new ZipEntry("project-export-v1/checksums/sha256sums.txt"));
            zos.write(checksums.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        byte[] zip = baos.toByteArray();

        ProjectExportZipReader.ZipReadResult result = reader.readArchive(
                new ByteArrayInputStream(zip), zip.length);

        // Checksum validation should pass (paths are normalized)
        assertFalse(result.errors().stream().anyMatch(e -> e.contains("Checksum mismatch")),
                "Checksum should match after normalization, got errors: " + result.errors());
    }
}
