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
 * <p>For every snapshot store/write the definition rows are re-scanned; the
 * same (definitionId, version) MUST map to exactly one content digest in the
 * DURABLE set. A different digest FAILS CLOSED. This replaces the InMemory
 * registry for production durability (the InMemory registry remains for
 * pure domain tests).
 */
public final class JdbcEffectDefinitionVersionRegistry implements EffectDefinitionVersionRegistry {

    private final DSLContext dsl;

    public JdbcEffectDefinitionVersionRegistry(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    @Override
    public void register(EffectDefinitionSnapshot definition) {
        Objects.requireNonNull(definition, "definition");
        // scan every durable Effect snapshot payload for the same (definitionId, version)
        java.util.List<String> payloads = dsl.fetch(
                        "select payload_json from timeline_snapshot where id like '"
                                + JdbcEffectSemanticSnapshotStore.ID_PREFIX + "%'")
                .getValues(0, String.class);
        for (String payload : payloads) {
            com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot snapshot;
            try {
                snapshot = com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotJsonCodec
                        .deserialize(payload);
            } catch (Exception e) {
                continue; // corrupt rows are rejected at read; registry scans valid rows
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
