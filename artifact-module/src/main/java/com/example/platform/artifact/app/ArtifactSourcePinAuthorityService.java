package com.example.platform.artifact.app;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Default {@link ArtifactSourcePinAuthority} over the canonical Artifact catalog read
 * projection ({@code artifact} table: identity, tenant, project, digest, lifecycle state).
 *
 * <p>Read-only: this service never registers, mutates or tombstones Artifacts. It is deliberately
 * independent of storage placement, render-job scope and the retired Media authority, so it works
 * for any pinned source, not only render outputs.
 */
@Service
public class ArtifactSourcePinAuthorityService implements ArtifactSourcePinAuthority {

    private final ArtifactCatalogService catalog;

    public ArtifactSourcePinAuthorityService(ArtifactCatalogService catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Override
    public PinResolution resolvePin(
            String tenantId, String projectId, ArtifactId artifactId, ContentDigest pinnedDigest) {
        String tenant = requireScope(tenantId, "tenantId");
        String project = requireScope(projectId, "projectId");
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(pinnedDigest, "pinnedDigest");
        String pinned = pinnedDigest.canonicalValue();

        var found = catalog.findArtifact(tenant, artifactId.value());
        if (found.isEmpty()) {
            return new PinResolution(
                    Outcome.UNKNOWN_ARTIFACT, artifactId, tenant, project, pinned, null, null);
        }
        var artifact = found.get();
        String recorded = artifact.checksum() == null ? null : artifact.checksum().toLowerCase();
        // The catalog's media slot carries artifact.media_type (the canonical coarse classification
        // of the Artifact's content), not a file format.
        String mediaType = normalize(artifact.format());
        if (!project.equals(artifact.projectId())) {
            return new PinResolution(
                    Outcome.OUT_OF_SCOPE, artifactId, tenant, project, pinned, recorded, mediaType);
        }
        if (!artifact.isUsable()) {
            return new PinResolution(
                    Outcome.NOT_USABLE, artifactId, tenant, project, pinned, recorded, mediaType);
        }
        if (recorded == null || !pinned.equals(recorded)) {
            return new PinResolution(
                    Outcome.PIN_MISMATCH, artifactId, tenant, project, pinned, recorded, mediaType);
        }
        return new PinResolution(
                Outcome.RESOLVED, artifactId, tenant, project, pinned, recorded, mediaType);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String requireScope(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("explicit " + name + " is required");
        }
        return value;
    }
}
