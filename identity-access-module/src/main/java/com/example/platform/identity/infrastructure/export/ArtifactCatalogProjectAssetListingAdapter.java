package com.example.platform.identity.infrastructure.export;

import com.example.platform.artifact.app.ArtifactCatalogService;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.identity.api.dto.ProjectResponse;
import com.example.platform.identity.app.TenantProjectService;
import com.example.platform.shared.export.ProjectAssetListingPort;
import com.example.platform.shared.export.ProjectAssetRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter that connects {@link ArtifactCatalogService} to the
 * {@link ProjectAssetListingPort} interface for listing project assets
 * during export.
 *
 * <p>Tenant scoping is enforced by verifying the project belongs to the
 * requesting tenant (via {@link TenantProjectService}) before querying artifacts.
 *
 * <p>This adapter lives in identity-access-module because it needs access to both:
 * <ul>
 *   <li>{@link TenantProjectService} (identity-access-module) for tenant scoping</li>
 *   <li>{@link ArtifactCatalogService} (artifact-catalog-module) for asset queries</li>
 * </ul>
 */
@Component
public class ArtifactCatalogProjectAssetListingAdapter implements ProjectAssetListingPort {

    private static final Logger log = LoggerFactory.getLogger(ArtifactCatalogProjectAssetListingAdapter.class);

    private final ArtifactCatalogService artifactCatalogService;
    private final TenantProjectService tenantProjectService;

    public ArtifactCatalogProjectAssetListingAdapter(ArtifactCatalogService artifactCatalogService,
                                                       TenantProjectService tenantProjectService) {
        this.artifactCatalogService = artifactCatalogService;
        this.tenantProjectService = tenantProjectService;
    }

    @Override
    public List<ProjectAssetRef> listAssets(String tenantId, String projectId) {
        // Enforce tenant boundary: verify project exists and belongs to tenant
        ProjectResponse project = tenantProjectService.getProject(projectId);
        if (project == null) {
            log.warn("listAssets: project not found: {}", projectId);
            return List.of();
        }
        if (!tenantId.equals(project.tenantId())) {
            log.warn("listAssets: tenant mismatch for project {} (expected={}, actual={})",
                    projectId, tenantId, project.tenantId());
            return List.of();
        }

        // Query artifacts by projectId
        List<Artifact> artifacts = artifactCatalogService.listArtifactsByProject(projectId);
        log.debug("listAssets: found {} artifacts for project {}", artifacts.size(), projectId);

        List<ProjectAssetRef> refs = new ArrayList<>();
        for (Artifact artifact : artifacts) {
            if (!artifact.isUsable()) {
                continue;
            }
            refs.add(new ProjectAssetRef(
                    artifact.id(),
                    mapFormatToType(artifact.format()),
                    mapFormatToMimeType(artifact.format()),
                    extractFilename(artifact),
                    artifact.sizeBytes(),
                    artifact.storageUri(),
                    artifact.checksum(),
                    artifact.duration() != null ? artifact.duration() * 1000L : null,
                    parseWidth(artifact.resolution()),
                    parseHeight(artifact.resolution())
            ));
        }
        return refs;
    }

    @Override
    public boolean isAvailable() {
        return true;
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
