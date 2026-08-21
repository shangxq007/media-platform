package com.example.platform.timeline.adapter;

import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotId;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotJsonCodec;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore;
import java.util.Objects;
import java.util.Optional;
import org.jooq.DSLContext;

/**
 * ROADMAP20 final implementation: durable jOOQ-backed
 * {@link EffectSemanticSnapshotStore} over the EXISTING {@code timeline_snapshot}
 * immutable row table (storage direction B, repository-reality adapted: this
 * repository's Flyway governance is V1-only — no new migration; the snapshot
 * rows use the {@code esnap_} id prefix and the effect contract version in
 * {@code schema_version}). NOT a second revision DAG.
 *
 * <p>Invariants enforced at the DB boundary:
 * <ul>
 *   <li>immutability: re-store with the SAME id and DIFFERENT content digest
 *       FAILS CLOSED (BI4) — enforced by digest comparison on conflict;</li>
 *   <li>idempotent re-store of identical content;</li>
 *   <li>exact historical lookup by id survives process restart (durable);</li>
 *   <li>missing snapshot row = Optional.empty (LEGACY MISSING, distinct from
 *       authoritative EMPTY).</li>
 * </ul>
 */
public final class JdbcEffectSemanticSnapshotStore implements EffectSemanticSnapshotStore {

    /** Id prefix keeps Effect snapshot rows distinct from timeline snapshot rows. */
    static final String ID_PREFIX = "esnap_";

    private final DSLContext dsl;
    private final String projectId;

    /** Production constructor: writes MUST go through storeTx(tx, projectId, snapshot). */
    public JdbcEffectSemanticSnapshotStore(DSLContext dsl) {
        this(dsl, "esnap");
    }

    /** Test/domain constructor with an explicit project for the non-transactional store(). */
    public JdbcEffectSemanticSnapshotStore(DSLContext dsl, String projectId) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
        this.projectId = Objects.requireNonNull(projectId, "projectId");
    }

    @Override
    public void store(EffectSemanticSnapshot snapshot) {
        // Non-transactional convenience (domain tests). Production canonical
        // writes MUST use storeTx(tx, projectId, snapshot) (§21) so the row
        // joins the revision's physical transaction and belongs to the real
        // product.
        storeTx(dsl, projectId, snapshot);
    }

    @Override
    public void storeTx(org.jooq.DSLContext tx, String projectId, EffectSemanticSnapshot snapshot) {
        Objects.requireNonNull(tx, "tx");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(snapshot, "snapshot");
        String payload = EffectSemanticSnapshotJsonCodec.serialize(snapshot);
        String id = snapshot.id().value();
        // atomic upsert with immutability check: same id, different digest -> fail closed
        // (runs on the SUPPLIED transaction DSL — no nested transaction, §21)
        String existingPayload = tx
                .fetch("select payload_json from timeline_snapshot where id = ?", id)
                .getValues(0, String.class)
                .stream().findFirst().orElse(null);
        if (existingPayload != null) {
            String existingDigest = existingPayload == null ? null
                    : extractDigest(existingPayload);
            if (existingDigest != null && !existingDigest.equals(snapshot.contentDigest())) {
                throw new IllegalArgumentException(
                        "SNAPSHOT IMMUTABILITY (BI4): snapshot id '" + id
                                + "' already stored with content digest '" + existingDigest
                                + "' — reusing an id with different semantic content FAILS CLOSED");
            }
            return; // idempotent for identical content (or legacy row absent digest)
        }
        tx.execute(
                "insert into timeline_snapshot "
                        + "(id, project_id, tenant_id, payload_json, schema_version, created_at) "
                        + "values (?, ?, ?, ?, ?, now())",
                id, projectId, null, payload,
                snapshot.semanticContractVersion().value());
    }

    @Override
    public Optional<EffectSemanticSnapshot> findById(EffectSemanticSnapshotId id) {
        Objects.requireNonNull(id, "id");
        String payload = dsl.fetch(
                        "select payload_json from timeline_snapshot where id = ?", id.value())
                .getValues(0, String.class)
                .stream().findFirst().orElse(null);
        if (payload == null) {
            return Optional.empty();
        }
        EffectSemanticSnapshot snapshot = EffectSemanticSnapshotJsonCodec.deserialize(payload);
        if (!snapshot.id().equals(id)) {
            throw new IllegalStateException("Corrupt snapshot row: stored id mismatch");
        }
        return Optional.of(snapshot);
    }

    private static String extractDigest(String payload) {
        try {
            return com.example.platform.timeline.app.InternalTimelineJson.mapper()
                    .readTree(payload).get("contentDigest").asText();
        } catch (Exception e) {
            return null;
        }
    }
}
