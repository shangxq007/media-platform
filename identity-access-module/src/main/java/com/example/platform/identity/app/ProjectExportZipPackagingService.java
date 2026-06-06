package com.example.platform.identity.app;

import com.example.platform.identity.api.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Packages a {@link ProjectExportResponse} into a zip archive following the
 * project-export-v1 directory structure.
 *
 * <p>This service only packages metadata (JSON manifests). It does NOT:
 * <ul>
 *   <li>Download or include real media files</li>
 *   <li>Follow signed URLs</li>
 *   <li>Write storageUri or storageRef into the archive</li>
 *   <li>Write signed URLs into the audit summary</li>
 * </ul>
 */
@Service
public class ProjectExportZipPackagingService {

    private static final Logger log = LoggerFactory.getLogger(ProjectExportZipPackagingService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // Allowed entry names — prevents zip slip
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
     * Package a metadata_only export response into a zip archive.
     *
     * @param response the export response to package
     * @return byte array of the zip archive
     * @throws IOException if zip creation fails
     */
    public byte[] packageMetadataOnly(ProjectExportResponse response) throws IOException {
        if (response == null) {
            throw new IllegalArgumentException("Export response cannot be null");
        }
        if (!"metadata_only".equals(response.exportMode()) && !"linked_assets".equals(response.exportMode())) {
            throw new IllegalArgumentException("Only metadata_only and linked_assets modes are supported for zip packaging. Got: " + response.exportMode());
        }

        Map<String, String> entries = buildEntries(response);
        Map<String, String> checksums = computeChecksums(entries);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Write all entries with canonical root prefix
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                String entryName = CANONICAL_ROOT + "/" + entry.getKey();
                validateEntryName(entryName);
                zos.putNextEntry(new ZipEntry(entryName));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            // Write checksums last with canonical root prefix
            String checksumsContent = buildSha256Sums(checksums);
            zos.putNextEntry(new ZipEntry(CANONICAL_ROOT + "/checksums/sha256sums.txt"));
            zos.write(checksumsContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        log.info("Packaged project export: exportId={} mode={} entries={} size={}bytes",
                response.exportId(), response.exportMode(), entries.size() + 1, baos.size());

        return baos.toByteArray();
    }

    private Map<String, String> buildEntries(ProjectExportResponse response) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();

        // manifest.json
        entries.put("manifest.json", serialize(buildManifest(response)));

        // project.json
        entries.put("project.json", serialize(response.project()));

        // assets.json (strip signed URLs and storageRef for security)
        entries.put("assets.json", serialize(sanitizeAssets(response.assets())));

        // timeline/timeline.json
        entries.put("timeline/timeline.json", serialize(response.timeline()));

        // render/render-plan.json
        if (response.render() != null) {
            Map<String, Object> rp = new LinkedHashMap<>();
            rp.put("schemaVersion", response.render().schemaVersion());
            rp.put("renderPlan", response.render().renderPlan() != null ? response.render().renderPlan() : Map.of());
            entries.put("render/render-plan.json", serialize(rp));

            // render/spatial-plan.json
            Map<String, Object> sp = new LinkedHashMap<>();
            sp.put("schemaVersion", response.render().schemaVersion());
            sp.put("spatialPlan", response.render().spatialPlan() != null ? response.render().spatialPlan() : Map.of());
            entries.put("render/spatial-plan.json", serialize(sp));

            // render/export-profiles.json (empty for now, reserved for future)
            Map<String, Object> ep = new LinkedHashMap<>();
            ep.put("schemaVersion", "project-export-v1");
            ep.put("profiles", List.of());
            entries.put("render/export-profiles.json", serialize(ep));
        }

        // effects/effect-taxonomy.json + applied-effects.json
        if (response.effects() != null) {
            Map<String, Object> taxonomy = new LinkedHashMap<>();
            taxonomy.put("schemaVersion", "project-export-v1");
            taxonomy.put("effectTaxonomyVersion", response.effects().effectTaxonomyVersion());
            entries.put("effects/effect-taxonomy.json", serialize(taxonomy));

            Map<String, Object> applied = new LinkedHashMap<>();
            applied.put("schemaVersion", "project-export-v1");
            applied.put("effects", response.effects().appliedEffects() != null ? response.effects().appliedEffects() : List.of());
            entries.put("effects/applied-effects.json", serialize(applied));
        }

        // outputs/outputs-manifest.json
        entries.put("outputs/outputs-manifest.json", serialize(response.outputs() != null ? response.outputs() : new ProjectExportOutputsDto("project-export-v1", 0, List.of())));

        // audit/audit-summary.json (strip sensitive data)
        entries.put("audit/audit-summary.json", serialize(sanitizeAudit(response.audit())));

        // README.md
        entries.put("README.md", buildReadme(response));

        return entries;
    }

    /**
     * Sanitize assets: remove signedUrls map, keep per-asset downloadUrl for
     * linked_assets mode but strip storageRef.
     */
    private ProjectExportAssetsDto sanitizeAssets(ProjectExportAssetsDto assets) {
        if (assets == null) {
            return new ProjectExportAssetsDto("project-export-v1", "metadata_only", List.of(), null);
        }
        // Clone assets with storageRef=null and no signedUrls map
        List<ProjectExportAssetDto> sanitized = new ArrayList<>();
        if (assets.assets() != null) {
            for (ProjectExportAssetDto a : assets.assets()) {
                sanitized.add(new ProjectExportAssetDto(
                        a.assetId(), a.filename(), a.type(), a.mimeType(),
                        a.sizeBytes(), a.checksum(), a.duration(),
                        a.width(), a.height(),
                        null, // storageRef always null
                        a.downloadUrl() // keep downloadUrl for linked_assets
                ));
            }
        }
        return new ProjectExportAssetsDto(
                assets.schemaVersion(), assets.exportMode(), sanitized, null);
    }

    /**
     * Sanitize audit: keep only safe fields.
     */
    private Map<String, Object> sanitizeAudit(ProjectExportAuditDto audit) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (audit != null) {
            safe.put("schemaVersion", audit.schemaVersion());
            safe.put("exportEventId", audit.exportEventId());
            safe.put("exportedAt", audit.exportedAt());
            safe.put("exportedBy", audit.exportedBy());
            safe.put("action", audit.action());
        }
        return safe;
    }

    /**
     * Build manifest for the zip package.
     */
    private ProjectExportManifestDto buildManifest(ProjectExportResponse response) {
        return new ProjectExportManifestDto(
                "project-export-v1",
                "project-export-v1",
                response.exportId(),
                response.exportMode(),
                response.exportedAt(),
                response.manifest() != null ? response.manifest().exportedBy() : "system",
                response.manifest() != null ? response.manifest().compatibility() : Map.of(),
                response.manifest() != null ? response.manifest().security() : new ProjectExportSecurityDto(false, false, false, false, true, true, true),
                response.manifest() != null ? response.manifest().assetsInfo() : new ProjectExportManifestAssetsDto("metadata_only", 0, 0),
                response.manifest() != null ? response.manifest().checksums() : Map.of("algorithm", "sha256")
        );
    }

    /**
     * Build README for the zip package.
     */
    private String buildReadme(ProjectExportResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Project Export Package\n\n");
        sb.append("- **Schema Version**: project-export-v1\n");
        sb.append("- **Export ID: ").append(response.exportId()).append("\n");
        sb.append("- **Export Mode**: ").append(response.exportMode()).append("\n");
        sb.append("- **Exported At: ").append(response.exportedAt()).append("\n");

        if (response.project() != null) {
            sb.append("- **Project: ").append(response.project().name()).append("\n");
            sb.append("- **Project ID: ").append(response.project().projectId()).append("\n");
        }

        sb.append("\n## Security\n\n");
        if (response.manifest() != null && response.manifest().security() != null) {
            var sec = response.manifest().security();
            sb.append("- Contains Signed URLs: ").append(sec.containsSignedUrls()).append("\n");
            sb.append("- Contains Media: ").append(sec.containsMedia()).append("\n");
            sb.append("- Contains Secrets: ").append(sec.containsSecrets()).append("\n");
            sb.append("- Contains Credentials: ").append(sec.containsCredentials()).append("\n");
            sb.append("- Prompt Redacted: ").append(sec.promptRedacted()).append("\n");
            sb.append("- History Redacted: ").append(sec.historyRedacted()).append("\n");
            sb.append("- Storage Refs Redacted: ").append(sec.storageRefsRedacted()).append("\n");
        }

        sb.append("\n## Contents\n\n");
        sb.append("- manifest.json: Export metadata and security flags\n");
        sb.append("- project.json: Project information\n");
        sb.append("- assets.json: Asset listing (no media files)\n");
        sb.append("- timeline/: Timeline structure\n");
        sb.append("- render/: Render and spatial plans\n");
        sb.append("- effects/: Effect taxonomy and applied effects\n");
        sb.append("- outputs/: Output manifests\n");
        sb.append("- audit/: Audit summary (no sensitive data)\n");
        sb.append("- checksums/: SHA-256 checksums for all entries\n");

        sb.append("\n## Import\n\n");
        sb.append("Use `POST /api/v1/identity/tenants/{tenantId}/project-imports/preview` to preview import.\n");
        sb.append("Use `POST /api/v1/identity/tenants/{tenantId}/project-imports` to execute import.\n");

        return sb.toString();
    }

    /**
     * Compute SHA-256 checksums for all entries.
     */
    private Map<String, String> computeChecksums(Map<String, String> entries) throws IOException {
        Map<String, String> checksums = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            checksums.put(entry.getKey(), sha256hex(entry.getValue().getBytes(StandardCharsets.UTF_8)));
        }
        return checksums;
    }

    /**
     * Build sha256sums.txt content.
     */
    private String buildSha256Sums(Map<String, String> checksums) {
        StringBuilder sb = new StringBuilder();
        sb.append("# SHA-256 checksums for project-export-v1\n");
        sb.append("# Format: <sha256> <canonical-path>\n\n");
        for (Map.Entry<String, String> entry : checksums.entrySet()) {
            // Use canonical path with root prefix
            sb.append(entry.getValue()).append("  ").append(CANONICAL_ROOT).append("/").append(entry.getKey()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Validate entry name — prevent zip slip.
     */
    private void validateEntryName(String entryName) {
        if (entryName == null || entryName.isBlank()) {
            throw new SecurityException("Zip entry name cannot be null or blank");
        }
        if (entryName.contains("..")) {
            throw new SecurityException("Zip entry name cannot contain '..': " + entryName);
        }
        if (entryName.startsWith("/") || entryName.startsWith("\\")) {
            throw new SecurityException("Zip entry name cannot start with / or \\: " + entryName);
        }
        // Strip canonical root prefix before allowlist check
        String stripped = entryName;
        String rootPrefix = CANONICAL_ROOT + "/";
        if (stripped.startsWith(rootPrefix)) {
            stripped = stripped.substring(rootPrefix.length());
        }
        if (!ALLOWED_ENTRIES.contains(stripped)) {
            throw new SecurityException("Zip entry name not in allowlist: " + entryName);
        }
    }

    private String serialize(Object obj) throws IOException {
        return MAPPER.writeValueAsString(obj);
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
}
