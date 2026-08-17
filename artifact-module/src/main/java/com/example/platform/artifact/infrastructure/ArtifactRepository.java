package com.example.platform.artifact.infrastructure;

import static com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT;
import static com.example.platform.typedschema.jooq.generated.tables.ArtifactReplica.ARTIFACT_REPLICA;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactReplicaBinding;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/**
 * GCR-2 (ARTIFACT_AUTHORITY_CONTRACT_V1): SOLE canonical Artifact persistence
 * adapter. artifact-module owns canonical Artifact identity/digest/replica rows
 * in the single V1 schema (artifact + artifact_replica). The storage-module
 * ArtifactRepository dual-writer is REPLACED by this adapter — storage keeps
 * only data-plane operations.
 */
@Repository
public class ArtifactRepository {

    private final DSLContext dsl;

    public ArtifactRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Artifact insert(Artifact artifact, String projectId, String renderJobId) {
        dsl.insertInto(ARTIFACT)
                .columns(ARTIFACT.ID, ARTIFACT.TENANT_ID, ARTIFACT.PROJECT_ID, ARTIFACT.RENDER_JOB_ID,
                        ARTIFACT.CONTENT_DIGEST, ARTIFACT.BYTE_LENGTH, ARTIFACT.MEDIA_TYPE,
                        ARTIFACT.ARTIFACT_KIND, ARTIFACT.STATE, ARTIFACT.SCHEMA_VERSION,
                        ARTIFACT.CREATED_AT, ARTIFACT.TOMBSTONED_AT)
                .values(artifact.artifactId().value(), artifact.tenantId(), projectId, renderJobId,
                        artifact.contentDigest().canonicalValue(), artifact.byteLength(),
                        artifact.mediaType().name(), artifact.artifactKind().name(),
                        artifact.state().name(), artifact.schemaVersion(),
                        toDb(artifact.createdAt()), null)
                .execute();
        return artifact;
    }

    /** Test/operational helper: insert a canonical row in an explicit state. */
    public void insertRaw(ArtifactId artifactId, String tenantId, ContentDigest digest, long byteLength,
            ArtifactMediaType mediaType, ArtifactKind kind, ArtifactState state, Instant tombstonedAt) {
        dsl.insertInto(ARTIFACT)
                .columns(ARTIFACT.ID, ARTIFACT.TENANT_ID, ARTIFACT.CONTENT_DIGEST, ARTIFACT.BYTE_LENGTH,
                        ARTIFACT.MEDIA_TYPE, ARTIFACT.ARTIFACT_KIND, ARTIFACT.STATE,
                        ARTIFACT.SCHEMA_VERSION, ARTIFACT.CREATED_AT, ARTIFACT.TOMBSTONED_AT)
                .values(artifactId.value(), tenantId, digest.canonicalValue(), byteLength,
                        mediaType.name(), kind.name(), state.name(), 1,
                        toDb(Instant.now()),
                        tombstonedAt == null ? null : toDb(tombstonedAt))
                .execute();
    }

    public void insertReplica(ArtifactReplicaBinding binding) {
        dsl.insertInto(ARTIFACT_REPLICA)
                .columns(ARTIFACT_REPLICA.ARTIFACT_ID, ARTIFACT_REPLICA.REPLICA_ID,
                        ARTIFACT_REPLICA.PROVIDER_ID, ARTIFACT_REPLICA.STORAGE_OBJECT_ID,
                        ARTIFACT_REPLICA.REGION, ARTIFACT_REPLICA.ROLE, ARTIFACT_REPLICA.STATE,
                        ARTIFACT_REPLICA.CREATED_AT)
                .values(binding.artifactId().value(), binding.storageReplicaId().value(),
                        binding.providerId().value(), binding.storageObjectId().value(),
                        binding.region(), binding.replicaRole().name(), "ACTIVE", toDb(binding.createdAt()))
                .execute();
    }

    public Optional<Artifact> findById(String tenantId, ArtifactId artifactId) {
        return dsl.selectFrom(ARTIFACT)
                .where(ARTIFACT.ID.eq(artifactId.value()).and(ARTIFACT.TENANT_ID.eq(tenantId)))
                .fetchOptional()
                .map(r -> new Artifact(
                        new ArtifactId(r.get(ARTIFACT.ID)),
                        r.get(ARTIFACT.TENANT_ID),
                        ContentDigest.sha256(r.get(ARTIFACT.CONTENT_DIGEST)),
                        r.get(ARTIFACT.BYTE_LENGTH),
                        ArtifactMediaType.valueOf(r.get(ARTIFACT.MEDIA_TYPE)),
                        ArtifactKind.valueOf(r.get(ARTIFACT.ARTIFACT_KIND)),
                        ArtifactState.valueOf(r.get(ARTIFACT.STATE)),
                        r.get(ARTIFACT.SCHEMA_VERSION),
                        r.get(ARTIFACT.CREATED_AT).toInstant(ZoneOffset.UTC)));
    }

    public List<Artifact> findByContentDigest(String tenantId, ContentDigest digest, int limit) {
        return dsl.selectFrom(ARTIFACT)
                .where(ARTIFACT.TENANT_ID.eq(tenantId)
                        .and(ARTIFACT.CONTENT_DIGEST.eq(digest.canonicalValue())))
                .limit(limit)
                .fetch()
                .map(r -> new Artifact(
                        new ArtifactId(r.get(ARTIFACT.ID)),
                        r.get(ARTIFACT.TENANT_ID),
                        ContentDigest.sha256(r.get(ARTIFACT.CONTENT_DIGEST)),
                        r.get(ARTIFACT.BYTE_LENGTH),
                        ArtifactMediaType.valueOf(r.get(ARTIFACT.MEDIA_TYPE)),
                        ArtifactKind.valueOf(r.get(ARTIFACT.ARTIFACT_KIND)),
                        ArtifactState.valueOf(r.get(ARTIFACT.STATE)),
                        r.get(ARTIFACT.SCHEMA_VERSION),
                        r.get(ARTIFACT.CREATED_AT).toInstant(ZoneOffset.UTC)));
    }

    public List<ArtifactReplicaBinding> listReplicas(String tenantId, ArtifactId artifactId) {
        // GCR2-CORRECTION-V1 (ARTIFACT_QUERY_TENANT_ARGUMENT_IS_SEMANTIC_NOT_DECORATIVE_V1):
        // scope through canonical Artifact ownership — replica metadata must not be
        // observable cross-tenant.
        return dsl.selectFrom(ARTIFACT_REPLICA)
                .where(ARTIFACT_REPLICA.ARTIFACT_ID.eq(artifactId.value())
                        .and(org.jooq.impl.DSL.exists(
                                dsl.selectOne()
                                        .from(ARTIFACT)
                                        .where(ARTIFACT.ID.eq(ARTIFACT_REPLICA.ARTIFACT_ID)
                                                .and(ARTIFACT.TENANT_ID.eq(tenantId))))))
                .fetch()
                .map(r -> new ArtifactReplicaBinding(
                        r.get(ARTIFACT_REPLICA.ARTIFACT_ID) + ":" + r.get(ARTIFACT_REPLICA.REPLICA_ID),
                        artifactId,
                        new StorageObjectId(r.get(ARTIFACT_REPLICA.STORAGE_OBJECT_ID)),
                        new StorageReplicaId(r.get(ARTIFACT_REPLICA.REPLICA_ID)),
                        new StorageProviderId(r.get(ARTIFACT_REPLICA.PROVIDER_ID)),
                        ReplicaRole.valueOf(r.get(ARTIFACT_REPLICA.ROLE)),
                        r.get(ARTIFACT_REPLICA.REGION) == null ? "default" : r.get(ARTIFACT_REPLICA.REGION),
                        r.get(ARTIFACT_REPLICA.CREATED_AT).toInstant(ZoneOffset.UTC)));
    }

    public Optional<ArtifactReplicaBinding> findReplica(String tenantId, ArtifactId artifactId, StorageReplicaId replicaId) {
        // GCR2-CORRECTION-V1: tenant-scoped through canonical Artifact ownership.
        return dsl.selectFrom(ARTIFACT_REPLICA)
                .where(ARTIFACT_REPLICA.ARTIFACT_ID.eq(artifactId.value())
                        .and(ARTIFACT_REPLICA.REPLICA_ID.eq(replicaId.value()))
                        .and(org.jooq.impl.DSL.exists(
                                dsl.selectOne()
                                        .from(ARTIFACT)
                                        .where(ARTIFACT.ID.eq(ARTIFACT_REPLICA.ARTIFACT_ID)
                                                .and(ARTIFACT.TENANT_ID.eq(tenantId))))))
                .fetchOptional()
                .map(r -> new ArtifactReplicaBinding(
                        r.get(ARTIFACT_REPLICA.ARTIFACT_ID) + ":" + r.get(ARTIFACT_REPLICA.REPLICA_ID),
                        artifactId,
                        new StorageObjectId(r.get(ARTIFACT_REPLICA.STORAGE_OBJECT_ID)),
                        new StorageReplicaId(r.get(ARTIFACT_REPLICA.REPLICA_ID)),
                        new StorageProviderId(r.get(ARTIFACT_REPLICA.PROVIDER_ID)),
                        ReplicaRole.valueOf(r.get(ARTIFACT_REPLICA.ROLE)),
                        r.get(ARTIFACT_REPLICA.REGION) == null ? "default" : r.get(ARTIFACT_REPLICA.REGION),
                        r.get(ARTIFACT_REPLICA.CREATED_AT).toInstant(ZoneOffset.UTC)));
    }

    public long countReplicas(String artifactId) {
        return dsl.fetchCount(ARTIFACT_REPLICA.where(ARTIFACT_REPLICA.ARTIFACT_ID.eq(artifactId)));
    }

    public void deleteReplica(String artifactId, String replicaId) {
        dsl.deleteFrom(ARTIFACT_REPLICA)
                .where(ARTIFACT_REPLICA.ARTIFACT_ID.eq(artifactId)
                        .and(ARTIFACT_REPLICA.REPLICA_ID.eq(replicaId)))
                .execute();
    }

    public boolean exists(String tenantId, ArtifactId artifactId) {
        return dsl.fetchExists(ARTIFACT.where(ARTIFACT.ID.eq(artifactId.value())
                .and(ARTIFACT.TENANT_ID.eq(tenantId))));
    }

    public void updateState(String artifactId, ArtifactState state, LocalDateTime tombstonedAt) {
        var step = dsl.update(ARTIFACT)
                .set(ARTIFACT.STATE, state.name())
                .set(ARTIFACT.TOMBSTONED_AT, tombstonedAt)
                .where(ARTIFACT.ID.eq(artifactId));
        step.execute();
    }

    public List<Artifact> findTombstonedBefore(String tenantId, Instant cutoff) {
        var condition = ARTIFACT.STATE.eq(ArtifactState.DELETING.name())
                .and(ARTIFACT.TOMBSTONED_AT.isNotNull())
                .and(ARTIFACT.TOMBSTONED_AT.lt(toDb(cutoff)));
        if (tenantId != null && !tenantId.equals("*")) {
            condition = condition.and(ARTIFACT.TENANT_ID.eq(tenantId));
        }
        return dsl.selectFrom(ARTIFACT)
                .where(condition)
                .fetch()
                .map(r -> new Artifact(
                        new ArtifactId(r.get(ARTIFACT.ID)),
                        r.get(ARTIFACT.TENANT_ID),
                        ContentDigest.sha256(r.get(ARTIFACT.CONTENT_DIGEST)),
                        r.get(ARTIFACT.BYTE_LENGTH),
                        ArtifactMediaType.valueOf(r.get(ARTIFACT.MEDIA_TYPE)),
                        ArtifactKind.valueOf(r.get(ARTIFACT.ARTIFACT_KIND)),
                        ArtifactState.valueOf(r.get(ARTIFACT.STATE)),
                        r.get(ARTIFACT.SCHEMA_VERSION),
                        r.get(ARTIFACT.CREATED_AT).toInstant(ZoneOffset.UTC)));
    }

    public void markPurged(String artifactId) {
        dsl.update(ARTIFACT)
                .set(ARTIFACT.STATE, ArtifactState.DELETED.name())
                .set(ARTIFACT.TOMBSTONED_AT, LocalDateTime.now(ZoneOffset.UTC))
                .where(ARTIFACT.ID.eq(artifactId))
                .execute();
    }

    private static LocalDateTime toDb(java.time.Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static java.time.Instant toInstant(LocalDateTime ts) {
        return ts == null ? null : ts.toInstant(ZoneOffset.UTC);
    }
}
