package com.example.platform.artifact.app;

import static com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT;

import com.example.platform.artifact.domain.ArtifactCatalogEntry;
import com.example.platform.artifact.domain.ArtifactStatus;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/**
 * GCR-2 (ARTIFACT_AUTHORITY_CONTRACT_V1 C6/C16): ArtifactCatalogEntry is a
 * READ/PROJECTION ONLY repository over the canonical {@code artifact} table.
 *
 * <p>The canonical Artifact identity/digest/replica truth is owned by
 * artifact-module's {@code ArtifactRepository} + {@code ArtifactCommitService}
 * (single write authority). This repository performs NO canonical writes:
 * catalog rebuild/delete cannot mutate canonical Artifact identity, and no
 * canonical mutation routes through the catalog. Storage URI is a projection
 * field (physical replica location), never identity.</p>
 */
@Repository
public class ArtifactCatalogRepository {

    private final DSLContext dsl;

    public ArtifactCatalogRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    private ArtifactCatalogEntry toEntry(org.jooq.Record r) {
        return new ArtifactCatalogEntry(
                r.get(ARTIFACT.ID),
                r.get(ARTIFACT.RENDER_JOB_ID),
                r.get(ARTIFACT.PROJECT_ID),
                r.get(ARTIFACT.MEDIA_TYPE),
                null,
                null,
                r.get(ARTIFACT.BYTE_LENGTH),
                r.get(ARTIFACT.CONTENT_DIGEST),
                statusFrom(r.get(ARTIFACT.STATE)),
                toInstant(r.get(ARTIFACT.TOMBSTONED_AT)),
                toInstant(r.get(ARTIFACT.CREATED_AT)));
    }

    public Optional<ArtifactCatalogEntry> findById(String tenantId, String id) {
        requireTenantId(tenantId);
        return dsl.selectFrom(ARTIFACT)
                .where(ARTIFACT.ID.eq(id).and(ARTIFACT.TENANT_ID.eq(tenantId)))
                .fetchOptional()
                .map(this::toEntry);
    }

    public int countAll() {
        return dsl.fetchCount(ARTIFACT);
    }

    private static ArtifactStatus statusFrom(String state) {
        if (state == null) {
            return ArtifactStatus.ACTIVE;
        }
        try {
            return switch (state) {
                case "DELETING", "DELETED" -> ArtifactStatus.TOMBSTONED;
                case "QUARANTINED", "FAILED" -> ArtifactStatus.TOMBSTONED;
                default -> ArtifactStatus.ACTIVE;
            };
        } catch (Exception e) {
            return ArtifactStatus.ACTIVE;
        }
    }

    private static java.time.Instant toInstant(LocalDateTime ts) {
        return ts == null ? null : ts.toInstant(ZoneOffset.UTC);
    }

    private static void requireTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank() || "*".equals(tenantId)) {
            throw new IllegalArgumentException("explicit tenantId is required");
        }
    }
}
