package com.example.platform.timeline.adapter;

import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotCanonicalSemantics;
import com.example.platform.timeline.semantics.effect.EffectDefinitionCanonicalSemantics;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotId;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotJsonCodec;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore;
import java.util.Objects;
import java.util.Optional;
import org.jooq.DSLContext;

/**
 * ROADMAP20 final implementation: DURABLE Effect semantic snapshot store —
 * immutable rows in the EXISTING {@code timeline_snapshot} table (V1-only
 * Flyway governance; Effect rows use the {@code esnap_} prefix; zero new
 * migrations).
 *
 * <p>Ownership (B4, EFFECT_SEMANTIC_SNAPSHOT_HAS_EXPLICIT_OWNERSHIP_V1):
 * every row is bound to (project_id, tenant_id). {@code storeTx} writes the
 * explicit tenant (never null); {@code findById} resolves ONLY within the
 * supplied (projectId, tenantId) — cross-project / cross-tenant authority is
 * impossible (CROSS_PROJECT_SNAPSHOT_AUTHORITY_IS_FORBIDDEN_V1,
 * CROSS_TENANT_SNAPSHOT_AUTHORITY_IS_FORBIDDEN_V1).
 *
 * <p>Corruption (CORRUPT_AUTHORITATIVE_EFFECT_SNAPSHOT_FAILS_CLOSED_V1): an
 * existing esnap_ row is validated by FULL deserialization — id match,
 * recomputed digest, supported contract, embedded definition digests. Partial
 * JSON field extraction is never authority. A row that cannot be decoded or
 * verified THROWS — no idempotent-success, no overwrite, no skip.
 *
 * <p>The non-transactional {@link #store} convenience is NOT a production
 * path: it throws (production writes MUST use {@link #storeTx} inside the
 * revision's physical transaction with explicit ownership).
 */
public final class JdbcEffectSemanticSnapshotStore implements EffectSemanticSnapshotStore {

    public static final String ID_PREFIX = "esnap_";

    private final DSLContext dsl;

    public JdbcEffectSemanticSnapshotStore(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    @Override
    public void store(EffectSemanticSnapshot snapshot) {
        // Production convenience ownership is forbidden (§22): the single-arg
        // constructor previously defaulted to a hardcoded "esnap" project —
        // that ambiguous ownership model is removed. Durable writes MUST flow
        // through storeTx(tx, projectId, tenantId, snapshot).
        throw new UnsupportedOperationException(
                "JdbcEffectSemanticSnapshotStore.store(): production writes MUST use "
                        + "storeTx(tx, projectId, tenantId, snapshot) with explicit ownership "
                        + "inside the canonical revision's physical transaction");
    }

    @Override
    public void storeTx(org.jooq.DSLContext tx, String projectId, String tenantId,
                        EffectSemanticSnapshot snapshot) {
        Objects.requireNonNull(tx, "tx");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(snapshot, "snapshot");
        String payload = EffectSemanticSnapshotJsonCodec.serialize(snapshot);
        String id = snapshot.id().value();
        // B4/SC1-SC5: existing row is validated by FULL deserialization — never
        // partial field extraction, never null-digest idempotent success.
        String existingPayload = tx
                .fetch("select payload_json from timeline_snapshot where id = ?", id)
                .getValues(0, String.class)
                .stream().findFirst().orElse(null);
        if (existingPayload != null) {
            validateExistingRow(id, snapshot, existingPayload);
            return; // idempotent for EXACT identical content (SC6)
        }
        tx.execute(
                "insert into timeline_snapshot "
                        + "(id, project_id, tenant_id, payload_json, schema_version, created_at) "
                        + "values (?, ?, ?, ?, ?, now())",
                id, projectId, tenantId, payload,
                snapshot.semanticContractVersion().value());
    }

    /**
     * B4/SC1-SC7: full validation of an existing authoritative row.
     * CORRUPT_AUTHORITATIVE_EFFECT_SNAPSHOT_FAILS_CLOSED_V1.
     */
    private void validateExistingRow(String id, EffectSemanticSnapshot incoming,
                                     String existingPayload) {
        EffectSemanticSnapshot existing;
        try {
            existing = EffectSemanticSnapshotJsonCodec.deserialize(existingPayload);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "CORRUPT_AUTHORITATIVE_EFFECT_SNAPSHOT_FAILS_CLOSED_V1: existing esnap_ row '"
                            + id + "' cannot be decoded — refusing idempotent success: "
                            + e.getMessage(), e);
        }
        // SC4: payload snapshot id must equal the DB row id
        if (!existing.id().value().equals(id)) {
            throw new IllegalStateException(
                    "CORRUPT_AUTHORITATIVE_EFFECT_SNAPSHOT_FAILS_CLOSED_V1 (SC4): payload "
                            + "snapshot id '" + existing.id().value() + "' differs from DB row id '"
                            + id + "'");
        }
        // SC2/SC3: recomputed digest must equal the stored digest
        String recomputed = com.example.platform.timeline.semantics.effect
                .EffectSemanticSnapshotCanonicalSemantics.snapshotContentDigest(existing);
        if (!recomputed.equals(existing.contentDigest())) {
            throw new IllegalStateException(
                    "CORRUPT_AUTHORITATIVE_EFFECT_SNAPSHOT_FAILS_CLOSED_V1 (SC2/SC3): existing "
                            + "esnap_ row '" + id + "' content digest '" + existing.contentDigest()
                            + "' does not match recomputed '" + recomputed + "'");
        }
        // SC5: supported semantic contract version
        if (existing.semanticContractVersion() == null
                || !com.example.platform.timeline.semantics.effect.EffectSemanticContractVersion
                        .isSupported(existing.semanticContractVersion().value())) {
            throw new IllegalStateException(
                    "CORRUPT_AUTHORITATIVE_EFFECT_SNAPSHOT_FAILS_CLOSED_V1 (SC5): existing "
                            + "esnap_ row '" + id + "' carries unsupported semantic contract "
                            + existing.semanticContractVersion());
        }
        // embedded definition digests must verify (CR3)
        for (var entry : existing.entries()) {
            com.example.platform.timeline.semantics.effect.EffectDefinitionCanonicalSemantics
                    .verifyDefinitionDigest(entry.definitionSnapshot());
        }
        // SC7: same id, different content -> IMMUTABILITY FAIL CLOSED
        if (!existing.contentDigest().equals(incoming.contentDigest())) {
            throw new IllegalArgumentException(
                    "EFFECT_SEMANTIC_SNAPSHOT_IMMUTABLE (BI4/SC7): snapshot id '" + id
                            + "' already stored with content digest '" + existing.contentDigest()
                            + "' — reusing an id with different semantic content FAILS CLOSED");
        }
        // SC6: exact identical content -> idempotent PASS
    }

    @Override
    public Optional<EffectSemanticSnapshot> findById(String projectId, String tenantId,
                                                     EffectSemanticSnapshotId id) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(tenantId, "tenantId");
        String payload = dsl.fetch(
                        "select payload_json from timeline_snapshot where id = ? and project_id = ? and tenant_id = ?",
                        id.value(), projectId, tenantId)
                .getValues(0, String.class)
                .stream().findFirst().orElse(null);
        if (payload == null) {
            return Optional.empty();
        }
        EffectSemanticSnapshot snapshot = EffectSemanticSnapshotJsonCodec.deserialize(payload);
        if (!snapshot.id().equals(id)) {
            throw new IllegalStateException(
                    "CORRUPT_AUTHORITATIVE_EFFECT_SNAPSHOT_FAILS_CLOSED_V1: stored snapshot id mismatch (CR5)");
        }
        return Optional.of(snapshot);
    }
}
