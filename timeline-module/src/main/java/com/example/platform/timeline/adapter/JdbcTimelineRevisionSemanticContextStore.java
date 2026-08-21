package com.example.platform.timeline.adapter;

import com.example.platform.timeline.app.InternalTimelineJson;
import com.example.platform.timeline.version.TimelineRevisionSemanticContext;
import com.example.platform.timeline.version.TimelineRevisionSemanticContextJsonCodec;
import java.util.Objects;
import java.util.Optional;
import org.jooq.DSLContext;

/**
 * ROADMAP20 authority-integration correction: durable persistence of the
 * revision-owned {@link TimelineRevisionSemanticContext} as a
 * {@code timeline_snapshot} row with the {@code revctx_} id prefix
 * ({@code revctx_<revisionId>}) — V1-only Flyway governance preserved (no new
 * table, no new migration, no new revision DAG).
 *
 * <p>The context row is revision-owned (1:1 with the revision id), immutable
 * (BI4-style: re-store with different digest FAILS CLOSED), and read
 * reconstructs the exact Effect pin WITHOUT caller input. Reads verify the
 * revision semantic digest by recomputation (DB pin tamper → FAIL CLOSED, §59).
 */
public final class JdbcTimelineRevisionSemanticContextStore
        implements com.example.platform.timeline.version.TimelineRevisionSemanticContextStore {

    public static final String ID_PREFIX = "revctx_";

    private final DSLContext dsl;

    public JdbcTimelineRevisionSemanticContextStore(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    /** Transaction-aware store — participates in the SAME physical tx as the revision write. */
    public void storeTx(DSLContext tx, String projectId, String revisionId,
            TimelineRevisionSemanticContext context) {
        Objects.requireNonNull(tx, "tx");
        Objects.requireNonNull(revisionId, "revisionId");
        Objects.requireNonNull(context, "context");
        String id = ID_PREFIX + revisionId;
        String payload = TimelineRevisionSemanticContextJsonCodec.serialize(context);
        String existing = tx.fetch("select payload_json from timeline_snapshot where id = ?", id)
                .getValues(0, String.class).stream().findFirst().orElse(null);
        if (existing != null) {
            if (!extractDigest(existing).equals(context.revisionSemanticDigest())) {
                throw new IllegalArgumentException(
                        "REVISION SEMANTIC CONTEXT IMMUTABILITY: revision " + revisionId
                                + " already owns a context with a different revision semantic digest — "
                                + "historical pin mutation FAILS CLOSED");
            }
            return; // idempotent
        }
        tx.execute("insert into timeline_snapshot "
                        + "(id, project_id, tenant_id, payload_json, schema_version, created_at) "
                        + "values (?, ?, ?, ?, ?, now())",
                id, projectId, null, payload, context.digestContractVersion());
    }

    /** Reads the revision-owned context; verification of the digest happens in the codec. */
    public Optional<TimelineRevisionSemanticContext> findByRevisionId(DSLContext readDsl, String revisionId) {
        Objects.requireNonNull(revisionId, "revisionId");
        String payload = readDsl.fetch(
                        "select payload_json from timeline_snapshot where id = ?", ID_PREFIX + revisionId)
                .getValues(0, String.class).stream().findFirst().orElse(null);
        if (payload == null) {
            return Optional.empty();
        }
        return Optional.of(TimelineRevisionSemanticContextJsonCodec.deserialize(payload));
    }

    public Optional<TimelineRevisionSemanticContext> findByRevisionId(String revisionId) {
        return findByRevisionId(dsl, revisionId);
    }

    private static String extractDigest(String payload) {
        try {
            return InternalTimelineJson.mapper().readTree(payload)
                    .get("revisionSemanticDigest").asText();
        } catch (Exception e) {
            return "";
        }
    }
}
