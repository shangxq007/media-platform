package com.example.platform.artifact.infrastructure;

import static com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN;

import com.example.platform.shared.digest.ContentDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/**
 * GCR-2 (ARTIFACT_AUTHORITY_CONTRACT_V1 C10/C14): historical revision Artifact
 * pin persistence. A pin records that a Timeline revision requires an exact
 * Artifact (id + content digest) for reproducibility. Pinned artifacts are
 * protected from destructive GC and from last-usable-replica deletion.
 */
@Repository
public class ArtifactPinRepository {

    private final DSLContext dsl;

    public ArtifactPinRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void insert(String pinId, String revisionId, String projectId, String artifactId, ContentDigest digest, java.time.Instant pinnedAt) {
        dsl.insertInto(ARTIFACT_PIN)
                .columns(ARTIFACT_PIN.PIN_ID, ARTIFACT_PIN.REVISION_ID, ARTIFACT_PIN.PROJECT_ID,
                        ARTIFACT_PIN.ARTIFACT_ID, ARTIFACT_PIN.CONTENT_DIGEST, ARTIFACT_PIN.PINNED_AT)
                .values(pinId, revisionId, projectId, artifactId, digest.canonicalValue(),
                        LocalDateTime.ofInstant(pinnedAt, ZoneOffset.UTC))
                .execute();
    }

    public boolean isPinned(String artifactId) {
        return dsl.fetchExists(ARTIFACT_PIN.where(ARTIFACT_PIN.ARTIFACT_ID.eq(artifactId)));
    }

    public Optional<String> findPinnedDigest(String artifactId) {
        return dsl.select(ARTIFACT_PIN.CONTENT_DIGEST)
                .from(ARTIFACT_PIN)
                .where(ARTIFACT_PIN.ARTIFACT_ID.eq(artifactId))
                .limit(1)
                .fetchOptional()
                .map(r -> r.get(ARTIFACT_PIN.CONTENT_DIGEST));
    }

    public List<String> listPinnedArtifactIds() {
        return dsl.selectDistinct(ARTIFACT_PIN.ARTIFACT_ID)
                .from(ARTIFACT_PIN)
                .fetch(ARTIFACT_PIN.ARTIFACT_ID);
    }

    public long countPinsForRevision(String revisionId) {
        return dsl.fetchCount(ARTIFACT_PIN.where(ARTIFACT_PIN.REVISION_ID.eq(revisionId)));
    }
}
