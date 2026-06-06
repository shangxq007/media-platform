package com.example.platform.identity.app;

import com.example.platform.identity.api.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class ProjectExportZipPackagingServiceTest {

    private ProjectExportZipPackagingService service;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        service = new ProjectExportZipPackagingService();
    }

    private ProjectExportResponse buildMetadataOnlyResponse() {
        Instant now = Instant.now();
        ProjectExportSecurityDto security = new ProjectExportSecurityDto(
                false, false, false, false, true, true, true);
        ProjectExportManifestAssetsDto assetsInfo = new ProjectExportManifestAssetsDto("metadata_only", 0, 0);
        ProjectExportManifestDto manifest = new ProjectExportManifestDto(
                "project-export-v1", "project-export-v1", "exp-123",
                "metadata_only", now, "user-1",
                Map.of("minPlatformVersion", "1.0.0"),
                security, assetsInfo, Map.of("algorithm", "sha256"));
        ProjectExportProjectDto project = new ProjectExportProjectDto(
                "prj-1", "tenant-1", "Test Project", "A test project", now, now, "ACTIVE");
        ProjectExportAssetsDto assets = new ProjectExportAssetsDto(
                "project-export-v1", "metadata_only", List.of(), null);
        ProjectExportTimelineDto timeline = new ProjectExportTimelineDto(
                "project-export-v1", List.of(), 0.0);
        ProjectExportRenderDto render = new ProjectExportRenderDto(
                "project-export-v1", Map.of(), Map.of(), "v1");
        ProjectExportEffectsDto effects = new ProjectExportEffectsDto(
                "project-export-v1", "v1", List.of());
        ProjectExportOutputsDto outputs = new ProjectExportOutputsDto(
                "project-export-v1", 0, List.of());
        ProjectExportAuditDto audit = new ProjectExportAuditDto(
                "project-export-v1", "aud-1", now, "user-1", "PROJECT_EXPORT");

        return new ProjectExportResponse(
                "project-export-v1", "exp-123", "metadata_only", now,
                manifest, project, assets, timeline, render, effects, outputs, audit);
    }

    private ProjectExportResponse buildLinkedAssetsResponse() {
        Instant now = Instant.now();
        ProjectExportSecurityDto security = new ProjectExportSecurityDto(
                true, false, false, false, true, true, true);
        ProjectExportManifestAssetsDto assetsInfo = new ProjectExportManifestAssetsDto("linked_assets", 2, 0);
        ProjectExportManifestDto manifest = new ProjectExportManifestDto(
                "project-export-v1", "project-export-v1", "exp-456",
                "linked_assets", now, "user-1",
                Map.of("minPlatformVersion", "1.0.0"),
                security, assetsInfo, Map.of("algorithm", "sha256"));
        ProjectExportProjectDto project = new ProjectExportProjectDto(
                "prj-2", "tenant-1", "Linked Project", "desc", now, now, "ACTIVE");

        ProjectExportAssetDto asset1 = new ProjectExportAssetDto(
                "asset-1", "video.mp4", "video", "video/mp4",
                1234567L, "sha256:abc123", 5.0, 1920, 1080,
                null, "https://signed.example.com/video?token=abc");
        ProjectExportAssetDto asset2 = new ProjectExportAssetDto(
                "asset-2", "audio.wav", "audio", "audio/wav",
                789012L, null, null, null, null,
                null, "https://signed.example.com/audio?token=def");

        ProjectExportAssetsDto assets = new ProjectExportAssetsDto(
                "project-export-v1", "linked_assets", List.of(asset1, asset2), null);
        ProjectExportTimelineDto timeline = new ProjectExportTimelineDto(
                "project-export-v1", List.of(), 0.0);
        ProjectExportRenderDto render = new ProjectExportRenderDto(
                "project-export-v1", Map.of(), Map.of(), "v1");
        ProjectExportEffectsDto effects = new ProjectExportEffectsDto(
                "project-export-v1", "v1", List.of());
        ProjectExportOutputsDto outputs = new ProjectExportOutputsDto(
                "project-export-v1", 0, List.of());
        ProjectExportAuditDto audit = new ProjectExportAuditDto(
                "project-export-v1", "aud-2", now, "user-1", "PROJECT_EXPORT");

        return new ProjectExportResponse(
                "project-export-v1", "exp-456", "linked_assets", now,
                manifest, project, assets, timeline, render, effects, outputs, audit);
    }

    // ─── Required entries ───

    @Test
    void zipShouldContainRequiredEntries() throws Exception {
        ProjectExportResponse response = buildMetadataOnlyResponse();
        byte[] zip = service.packageMetadataOnly(response);

        Set<String> entryNames = listZipEntries(zip);

        // All entries should have canonical root prefix
        assertTrue(entryNames.contains("project-export-v1/manifest.json"));
        assertTrue(entryNames.contains("project-export-v1/project.json"));
        assertTrue(entryNames.contains("project-export-v1/assets.json"));
        assertTrue(entryNames.contains("project-export-v1/timeline/timeline.json"));
        assertTrue(entryNames.contains("project-export-v1/render/render-plan.json"));
        assertTrue(entryNames.contains("project-export-v1/render/spatial-plan.json"));
        assertTrue(entryNames.contains("project-export-v1/render/export-profiles.json"));
        assertTrue(entryNames.contains("project-export-v1/effects/effect-taxonomy.json"));
        assertTrue(entryNames.contains("project-export-v1/effects/applied-effects.json"));
        assertTrue(entryNames.contains("project-export-v1/outputs/outputs-manifest.json"));
        assertTrue(entryNames.contains("project-export-v1/audit/audit-summary.json"));
        assertTrue(entryNames.contains("project-export-v1/checksums/sha256sums.txt"));
        assertTrue(entryNames.contains("project-export-v1/README.md"));

        // No entries without prefix
        assertFalse(entryNames.contains("manifest.json"), "Should not have entries without root prefix");
    }

    // ─── JSON structure validation ───

    @Test
    void zipEntriesShouldBeValidJson() throws Exception {
        ProjectExportResponse response = buildMetadataOnlyResponse();
        byte[] zip = service.packageMetadataOnly(response);

        Map<String, String> contents = readZipContents(zip);

        mapper.readTree(contents.get("project-export-v1/manifest.json"));
        mapper.readTree(contents.get("project-export-v1/project.json"));
        mapper.readTree(contents.get("project-export-v1/assets.json"));
        mapper.readTree(contents.get("project-export-v1/timeline/timeline.json"));
        mapper.readTree(contents.get("project-export-v1/effects/effect-taxonomy.json"));
        mapper.readTree(contents.get("project-export-v1/outputs/outputs-manifest.json"));
        mapper.readTree(contents.get("project-export-v1/audit/audit-summary.json"));
    }

    @Test
    void manifestJsonShouldContainRequiredFields() throws Exception {
        ProjectExportResponse response = buildMetadataOnlyResponse();
        byte[] zip = service.packageMetadataOnly(response);

        Map<String, String> contents = readZipContents(zip);
        JsonNode manifest = mapper.readTree(contents.get("project-export-v1/manifest.json"));

        assertNotNull(manifest.get("schemaVersion"));
        assertNotNull(manifest.get("exportMode"));
        assertNotNull(manifest.get("exportId"));
        assertEquals("project-export-v1", manifest.get("schemaVersion").asText());
        assertEquals("metadata_only", manifest.get("exportMode").asText());
        assertEquals("exp-123", manifest.get("exportId").asText());
    }

    @Test
    void projectJsonShouldContainProjectInfo() throws Exception {
        ProjectExportResponse response = buildMetadataOnlyResponse();
        byte[] zip = service.packageMetadataOnly(response);

        Map<String, String> contents = readZipContents(zip);
        JsonNode project = mapper.readTree(contents.get("project-export-v1/project.json"));

        assertNotNull(project.get("projectId"));
        assertNotNull(project.get("name"));
        assertEquals("prj-1", project.get("projectId").asText());
        assertEquals("Test Project", project.get("name").asText());
    }

    @Test
    void assetsJsonShouldContainAssetsArray() throws Exception {
        ProjectExportResponse response = buildLinkedAssetsResponse();
        byte[] zip = service.packageMetadataOnly(response);

        Map<String, String> contents = readZipContents(zip);
        JsonNode assets = mapper.readTree(contents.get("project-export-v1/assets.json"));

        assertTrue(assets.has("assets"));
        assertTrue(assets.get("assets").isArray());
        assertEquals(2, assets.get("assets").size());
    }

    @Test
    void auditSummaryJsonShouldNotContainSensitiveData() throws Exception {
        ProjectExportResponse response = buildLinkedAssetsResponse();
        byte[] zip = service.packageMetadataOnly(response);

        Map<String, String> contents = readZipContents(zip);
        JsonNode audit = mapper.readTree(contents.get("project-export-v1/audit/audit-summary.json"));

        assertFalse(audit.has("storageUri"), "Audit should not have storageUri");
        assertFalse(audit.has("signedUrl"), "Audit should not have signedUrl");
        assertFalse(audit.has("downloadUrl"), "Audit should not have downloadUrl");
        assertFalse(audit.has("bucket"), "Audit should not have bucket");
        assertFalse(audit.has("key"), "Audit should not have key");
    }

    // ─── Checksum validation ───

    @Test
    void zipChecksumsShouldMatchEntryContent() throws Exception {
        ProjectExportResponse response = buildMetadataOnlyResponse();
        byte[] zip = service.packageMetadataOnly(response);

        Map<String, String> contents = readZipContents(zip);
        String checksumsContent = contents.get("project-export-v1/checksums/sha256sums.txt");
        assertNotNull(checksumsContent);

        // Parse checksums file
        Map<String, String> expectedChecksums = new LinkedHashMap<>();
        for (String line : checksumsContent.split("\n")) {
            line = line.trim();
            if (line.startsWith("#") || line.isEmpty()) continue;
            String[] parts = line.split("  ", 2);
            if (parts.length == 2) {
                expectedChecksums.put(parts[1].trim(), parts[0].trim());
            }
        }

        // Verify each entry's checksum
        for (Map.Entry<String, String> entry : contents.entrySet()) {
            String entryName = entry.getKey();
            if (entryName.endsWith("checksums/sha256sums.txt")) continue; // skip self

            String expected = expectedChecksums.get(entryName);
            assertNotNull(expected, "sha256sums.txt should contain entry: " + entryName);

            String actual = sha256hex(entry.getValue().getBytes(StandardCharsets.UTF_8));
            assertEquals(expected, actual, "Checksum mismatch for " + entryName);
        }
    }

    @Test
    void zipChecksumsFileShouldNotReferenceItself() throws Exception {
        ProjectExportResponse response = buildMetadataOnlyResponse();
        byte[] zip = service.packageMetadataOnly(response);

        Map<String, String> contents = readZipContents(zip);
        String checksums = contents.get("project-export-v1/checksums/sha256sums.txt");

        assertFalse(checksums.contains("sha256sums.txt"), "Checksums file should not reference itself");
    }

    // ─── Storage / security ───

    @Test
    void zipShouldNotContainStorageUri() throws Exception {
        ProjectExportResponse response = buildLinkedAssetsResponse();
        byte[] zip = service.packageMetadataOnly(response);

        Map<String, String> contents = readZipContents(zip);
        String assetsJson = contents.get("project-export-v1/assets.json");

        assertFalse(assetsJson.contains("storageRef"), "assets.json should not contain storageRef");
    }

    @Test
    void zipShouldNotContainMediaFilesForMetadataOnly() throws Exception {
        ProjectExportResponse response = buildMetadataOnlyResponse();
        byte[] zip = service.packageMetadataOnly(response);

        Set<String> entries = listZipEntries(zip);

        for (String entry : entries) {
            assertFalse(entry.endsWith(".mp4"), "Should not contain .mp4: " + entry);
            assertFalse(entry.endsWith(".wav"), "Should not contain .wav: " + entry);
            assertFalse(entry.endsWith(".png"), "Should not contain .png: " + entry);
            assertFalse(entry.endsWith(".jpg"), "Should not contain .jpg: " + entry);
        }
    }

    @Test
    void metadataOnlyZipShouldNotRequireStorageProvider() throws Exception {
        ProjectExportResponse response = buildMetadataOnlyResponse();
        byte[] zip = service.packageMetadataOnly(response);

        assertNotNull(zip);
        assertTrue(zip.length > 0);

        Map<String, String> contents = readZipContents(zip);
        String readme = contents.get("project-export-v1/README.md");
        assertNotNull(readme);
        assertTrue(readme.contains("Schema Version"));
        assertTrue(readme.contains("metadata_only"));
    }

    // ─── Linked assets zip ───

    @Test
    void linkedAssetsZipShouldPreserveDownloadUrl() throws Exception {
        ProjectExportResponse response = buildLinkedAssetsResponse();
        byte[] zip = service.packageMetadataOnly(response);

        Map<String, String> contents = readZipContents(zip);
        String assetsJson = contents.get("project-export-v1/assets.json");

        assertTrue(assetsJson.contains("signed.example.com"), "Should contain signed URLs");
        assertTrue(assetsJson.contains("token=abc"), "Should contain downloadUrl");
    }

    @Test
    void linkedAssetsZipShouldNotContainSignedUrlsMap() throws Exception {
        ProjectExportResponse response = buildLinkedAssetsResponse();
        byte[] zip = service.packageMetadataOnly(response);

        Map<String, String> contents = readZipContents(zip);
        String assetsJson = contents.get("project-export-v1/assets.json");

        assertFalse(assetsJson.contains("\"signedUrls\""), "Should not contain signedUrls map");
    }

    // ─── Audit ───

    @Test
    void zipShouldNotContainAuditSensitiveData() throws Exception {
        ProjectExportResponse response = buildMetadataOnlyResponse();
        byte[] zip = service.packageMetadataOnly(response);

        Map<String, String> contents = readZipContents(zip);
        String auditJson = contents.get("project-export-v1/audit/audit-summary.json");

        assertFalse(auditJson.contains("storageUri"), "Audit should not contain storageUri");
        assertFalse(auditJson.contains("signedUrl"), "Audit should not contain signedUrl");
        assertFalse(auditJson.contains("downloadUrl"), "Audit should not contain downloadUrl");
    }

    // ─── Error cases ───

    @Test
    void zipShouldRejectNullResponse() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.packageMetadataOnly(null);
        });
    }

    @Test
    void zipShouldRejectUnsupportedMode() {
        ProjectExportResponse response = new ProjectExportResponse(
                "project-export-v1", "exp-999", "bundled_assets", Instant.now(),
                null, null, null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> {
            service.packageMetadataOnly(response);
        });
    }

    // ─── Zip slip prevention ───

    @Test
    void zipEntryNamesShouldNotContainPathSeparators() throws Exception {
        ProjectExportResponse response = buildMetadataOnlyResponse();
        byte[] zip = service.packageMetadataOnly(response);

        Set<String> entries = listZipEntries(zip);
        for (String entry : entries) {
            assertFalse(entry.contains(".."), "Entry should not contain '..': " + entry);
            assertFalse(entry.startsWith("/"), "Entry should not start with '/': " + entry);
            assertFalse(entry.startsWith("\\"), "Entry should not start with '\\': " + entry);
        }
    }

    // ─── helpers ───

    private Set<String> listZipEntries(byte[] zip) throws Exception {
        Set<String> entries = new LinkedHashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
                zis.closeEntry();
            }
        }
        return entries;
    }

    private Map<String, String> readZipContents(byte[] zip) throws Exception {
        Map<String, String> contents = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                byte[] data = zis.readAllBytes();
                contents.put(entry.getName(), new String(data, java.nio.charset.StandardCharsets.UTF_8));
                zis.closeEntry();
            }
        }
        return contents;
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
}
