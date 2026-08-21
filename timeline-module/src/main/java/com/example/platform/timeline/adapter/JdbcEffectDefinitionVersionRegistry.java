package com.example.platform.timeline.adapter;

import com.example.platform.timeline.semantics.effect.EffectDefinitionSnapshot;
import com.example.platform.timeline.semantics.effect.EffectDefinitionVersionRegistry;
import java.util.Objects;
import org.jooq.DSLContext;

/**
 * ROADMAP20 final implementation: DURABLE enforcement of
 * EFFECT_DEFINITION_VERSION_CONTENT_IS_IMMUTABLE_V1 across ALL authoritative
 * snapshots and across process restarts — backed by the EXISTING
 * {@code timeline_snapshot} immutable rows (V1-only Flyway governance; Effect
 * rows use the {@code esnap_} prefix, no new migration — repository-reality
 * adapted). The snapshot rows are the only definition sources (D1 embed).
 *
 * <p>Concurrency safety (§12/§13): {@code registerTx} takes a PostgreSQL
 * transaction-scoped advisory lock keyed by {@code hashtext(definitionId@version)}
 * BEFORE scanning durable rows, so two concurrent writers for the same
 * (definitionId, version) serialize; the second observes the first's committed
 * row and FAILS CLOSED on digest conflict. Never both authoritative.
 *
 * <p>Corrupt authoritative esnap_ rows (§14): a payload that cannot be decoded
 * is FAIL CLOSED — never silently skipped during identity verification.
 */
public final class JdbcEffectDefinitionVersionRegistry implements EffectDefinitionVersionRegistry {

    private final DSLContext dsl;

    public JdbcEffectDefinitionVersionRegistry(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    @Override
    public void register(EffectDefinitionSnapshot definition) {
        // Non-transactional convenience (domain tests). Production canonical
        // writes MUST use registerTx(tx, definition) so the advisory lock and
        // the scan join the revision's physical transaction (§12/§21).
        dsl.transactionResult(tx -> {
            registerTx(tx.dsl(), definition);
            return null;
        });
    }

    @Override
    public void registerTx(DSLContext tx, EffectDefinitionSnapshot definition) {
        Objects.requireNonNull(tx, "tx");
        Objects.requireNonNull(definition, "definition");
        // §13: transaction-scoped advisory lock serializes concurrent writers
        // for the same (definitionId, version) — the second writer sees the
        // first's committed row and fails closed on digest conflict. A bounded
        // lock timeout keeps pathological contention observable instead of
        // hanging the writer.
        tx.execute("set local lock_timeout = '30s'");
        tx.execute("select pg_advisory_xact_lock(hashtext(?))",
                definition.definitionId() + "@" + definition.version());
        // scan every durable Effect snapshot payload (via the SAME transaction)
        // for the same (definitionId, version)
        java.util.List<String> payloads = tx.fetch(
                        "select payload_json from timeline_snapshot where id like '"
                                + JdbcEffectSemanticSnapshotStore.ID_PREFIX + "%'")
                .getValues(0, String.class);
        if ("true".equalsIgnoreCase(System.getenv("RM20_REGISTRY_DIAG"))) {
            System.out.println("[REGISTRY-DIAG] scan rows=" + payloads.size()
                    + " def=" + definition.definitionId() + "@" + definition.version()
                    + " digest=" + definition.definitionContentDigest());
        }
        for (String payload : payloads) {
            com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot snapshot;
            try {
                snapshot = com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotJsonCodec
                        .deserialize(payload);
            } catch (Exception e) {
                // §14: corrupt AUTHORITATIVE esnap_ row is FAIL CLOSED — never
                // silently skipped during definition-version integrity checks.
                throw new IllegalStateException(
                        "CORRUPT_AUTHORITATIVE_EFFECT_SNAPSHOT_ROW (FAIL CLOSED): an esnap_ row "
                                + "cannot be decoded during definition-version identity verification — "
                                + "refusing to continue semantic integrity checks: " + e.getMessage(), e);
            }
            for (var entry : snapshot.entries()) {
                EffectDefinitionSnapshot d = entry.definitionSnapshot();
                if (d.definitionId().equals(definition.definitionId())
                        && d.version().equals(definition.version())) {
                    if (!d.definitionContentDigest().equals(definition.definitionContentDigest())) {
                        throw new IllegalArgumentException(
                                "EFFECT_DEFINITION_VERSION_CONTENT_IS_IMMUTABLE_V1 (D1): definition "
                                        + definition.definitionId() + "@" + definition.version()
                                        + " exists in durable snapshot with digest '" + d.definitionContentDigest()
                                        + "' but a different digest '" + definition.definitionContentDigest()
                                        + "' was supplied — same (id, version) MUST have exactly one "
                                        + "semantic content digest");
                    }
                }
            }
        }
    }
}
