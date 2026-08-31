package com.example.platform.artifact.infrastructure;

import static com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.artifact.app.ArtifactPinIdentity;
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

    public void insert(String pinId, String revisionId, String projectId, String tenantId,
            String artifactId, ContentDigest digest, java.time.Instant pinnedAt) {
        dsl.insertInto(ARTIFACT_PIN)
                .columns(ARTIFACT_PIN.PIN_ID, ARTIFACT_PIN.TENANT_ID,
                        ARTIFACT_PIN.REVISION_ID, ARTIFACT_PIN.PROJECT_ID,
                        ARTIFACT_PIN.ARTIFACT_ID, ARTIFACT_PIN.CONTENT_DIGEST, ARTIFACT_PIN.PINNED_AT)
                .values(pinId, tenantId, revisionId, projectId, artifactId, digest.canonicalValue(),
                        LocalDateTime.ofInstant(pinnedAt, ZoneOffset.UTC))
                .execute();
    }

    /**
     * R4-D1/D2 (CHECKPOINT_A Round 4): transaction-aware insert — executes on
     * the CALLER's DSLContext so the pin row joins the same physical database
     * transaction as the revision write. Artifact remains the pin persistence
     * authority (this SQL); Timeline never inserts ARTIFACT_PIN rows itself.
     */
    public void insertTx(org.jooq.DSLContext tx, String pinId, String revisionId,
            String projectId, String tenantId, String artifactId,
            ContentDigest digest, java.time.Instant pinnedAt) {
        tx.insertInto(ARTIFACT_PIN)
                .columns(ARTIFACT_PIN.PIN_ID, ARTIFACT_PIN.TENANT_ID,
                        ARTIFACT_PIN.REVISION_ID, ARTIFACT_PIN.PROJECT_ID,
                        ARTIFACT_PIN.ARTIFACT_ID, ARTIFACT_PIN.CONTENT_DIGEST, ARTIFACT_PIN.PINNED_AT)
                .values(pinId, tenantId, revisionId, projectId, artifactId, digest.canonicalValue(),
                        LocalDateTime.ofInstant(pinnedAt, ZoneOffset.UTC))
                .execute();
    }

    /**
     * R4-D1: copy the exact pin records of one revision onto another revision id
     * (same project, same artifacts, same digests) inside the caller's
     * transaction. Immutable historical pin contract is preserved — no
     * re-resolution of mutable latest Artifact state.
     */
    public void copyPinsTx(org.jooq.DSLContext tx, String tenantId, String projectId,
            String fromRevisionId, String toRevisionId) {
        var sourcePins = tx.select(
                        ARTIFACT_PIN.ARTIFACT_ID,
                        ARTIFACT_PIN.CONTENT_DIGEST,
                        ARTIFACT_PIN.PINNED_AT)
                .from(ARTIFACT_PIN)
                .where(ARTIFACT_PIN.REVISION_ID.eq(fromRevisionId))
                .and(ARTIFACT_PIN.TENANT_ID.eq(tenantId))
                .and(ARTIFACT_PIN.PROJECT_ID.eq(projectId))
                .fetch();
        for (var sourcePin : sourcePins) {
            String artifactId = sourcePin.get(ARTIFACT_PIN.ARTIFACT_ID);
            tx.insertInto(ARTIFACT_PIN)
                    .columns(ARTIFACT_PIN.PIN_ID, ARTIFACT_PIN.TENANT_ID,
                            ARTIFACT_PIN.REVISION_ID, ARTIFACT_PIN.PROJECT_ID,
                            ARTIFACT_PIN.ARTIFACT_ID, ARTIFACT_PIN.CONTENT_DIGEST,
                            ARTIFACT_PIN.PINNED_AT)
                    .values(ArtifactPinIdentity.forRevisionArtifact(
                                    tenantId, projectId, toRevisionId, artifactId),
                            tenantId, toRevisionId, projectId, artifactId,
                            sourcePin.get(ARTIFACT_PIN.CONTENT_DIGEST),
                            sourcePin.get(ARTIFACT_PIN.PINNED_AT))
                    .execute();
        }
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
