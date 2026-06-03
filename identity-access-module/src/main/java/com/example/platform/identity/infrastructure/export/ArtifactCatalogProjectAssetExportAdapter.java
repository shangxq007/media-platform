package com.example.platform.identity.infrastructure.export;

import com.example.platform.artifact.app.ArtifactCatalogService;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.identity.api.dto.ProjectResponse;
import com.example.platform.identity.app.ProjectExportService;
import com.example.platform.identity.app.TenantProjectService;
import com.example.platform.shared.asset.AssetDownloadUrlPort;
import com.example.platform.shared.export.ProjectAssetDescriptor;
import com.example.platform.shared.export.ProjectAssetExportPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Adapter that connects ArtifactCatalogService + AssetDownloadUrlPort
 * to the ProjectAssetExportPort interface for linked_assets project export.
 *
 * <p>Tenant scoping is enforced by verifying the project belongs to the
 * requesting tenant (via TenantProjectService) before querying artifacts.
 *
 * <p>This adapter lives in identity-access-module because it needs access to both:
 * <ul>
 *   <li>TenantProjectService (identity-access-module) for tenant scoping</li>
 *   <li>ArtifactCatalogService (artifact-catalog-module) for asset queries</li>
 *   <li>AssetDownloadUrlPort (storage-module) for signed URL generation</li>
 * </ul>
 */
@Component
public class ArtifactCatalogProjectAssetExportAdapter implements ProjectAssetExportPort {

    private static final Logger log = LoggerFactory.getLogger(ArtifactCatalogProjectAssetExportAdapter.class);

    private final ArtifactCatalogService artifactCatalogService;
    private final TenantProjectService tenantProjectService;
    private final AssetDownloadUrlPort assetDownloadUrlPort;

    public ArtifactCatalogProjectAssetExportAdapter(ArtifactCatalogService artifactCatalogService,
                                                      TenantProjectService tenantProjectService,
                                                      AssetDownloadUrlPort assetDownloadUrlPort) {
        this.artifactCatalogService = artifactCatalogService;
        this.tenantProjectService = tenantProjectService;
        this.assetDownloadUrlPort = assetDownloadUrlPort;
    }

    @Override
    public List<ProjectAssetDescriptor> listProjectAssets(String projectId) {
        // Verify project exists (tenant scoping is done by ProjectExportService before calling)
        ProjectResponse project = tenantProjectService.getProject(projectId);
        if (project == null) {
            log.warn("listProjectAssets: project not found: {}", projectId);
            return List.of();
        }

        // Query artifacts by projectId
        List<Artifact> artifacts = artifactCatalogService.listArtifactsByProject(projectId);
        log.debug("listProjectAssets: found {} artifacts for project {}", artifacts.size(), projectId);

        List<ProjectAssetDescriptor> descriptors = new ArrayList<>();
        for (Artifact artifact : artifacts) {
            if (!artifact.isUsable()) {
                continue;
            }
            descriptors.add(new ProjectAssetDescriptor(
                    artifact.id(),
                    extractFilename(artifact),
                    mapFormatToType(artifact.format()),
                    mapFormatToMimeType(artifact.format()),
                    0,
                    null,
                    artifact.duration() != null ? artifact.duration().doubleValue() : null,
                    parseWidth(artifact.resolution()),
                    parseHeight(artifact.resolution()),
                    artifact.storageUri()
            ));
        }
        return descriptors;
    }

    @Override
    public Optional<String> generateSignedAssetUrl(String projectId, String assetId,
                                                     String storageUri, Duration ttl) {
        if (storageUri == null || storageUri.isBlank()) {
            log.warn("generateSignedAssetUrl: asset {} has no storageUri", assetId);
            return Optional.empty();
        }
        return assetDownloadUrlPort.generateSignedUrl(assetId, storageUri, ttl);
    }

    @Override
    public boolean isAvailable() {
        return assetDownloadUrlPort != null && assetDownloadUrlPort.isAvailable();
    }

    private String extractFilename(Artifact artifact) {
        if (artifact.storageUri() != null) {
            int lastSlash = artifact.storageUri().lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < artifact.storageUri().length() - 1) {
                return artifact.storageUri().substring(lastSlash + 1);
            }
        }
        return artifact.id();
    }

    private String mapFormatToType(String format) {
        if (format == null) return "unknown";
        return switch (format.toLowerCase()) {
            case "mp4", "mov", "mkv", "webm" -> "video";
            case "png", "jpg", "jpeg", "gif", "webp" -> "image";
            case "wav", "mp3", "aac", "flac" -> "audio";
            case "srt", "vtt" -> "subtitle";
            default -> "unknown";
        };
    }

    private String mapFormatToMimeType(String format) {
        if (format == null) return "application/octet-stream";
        return switch (format.toLowerCase()) {
            case "mp4" -> "video/mp4";
            case "mov" -> "video/quicktime";
            case "webm" -> "video/webm";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "wav" -> "audio/wav";
            case "mp3" -> "audio/mpeg";
            case "srt" -> "application/x-subrip";
            case "vtt" -> "text/vtt";
            default -> "application/octet-stream";
        };
    }

    private Integer parseWidth(String resolution) {
        if (resolution == null) return null;
        int x = resolution.indexOf('x');
        if (x > 0) {
            try { return Integer.parseInt(resolution.substring(0, x).trim()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private Integer parseHeight(String resolution) {
        if (resolution == null) return null;
        int x = resolution.indexOf('x');
        if (x > 0 && x < resolution.length() - 1) {
            try { return Integer.parseInt(resolution.substring(x + 1).trim()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
