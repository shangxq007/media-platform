package com.example.platform.timeline.app;

import com.example.platform.timeline.revisioncommand.RevisionRef;
import java.time.LocalDateTime;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import static com.example.platform.typedschema.jooq.generated.tables.TimelineRevisionRef.TIMELINE_REVISION_REF;

/**
 * Shared typed mutation authority for the canonical mutable Timeline ref.
 * Callers own transaction orchestration and translate a false result to their
 * application/domain stale-ref error.
 */
public final class TimelineRevisionRefMutation {

    private final DSLContext dsl;

    public TimelineRevisionRefMutation(DSLContext dsl) {
        this.dsl = java.util.Objects.requireNonNull(dsl, "dsl");
    }

    public boolean advance(DSLContext tx, RevisionRef ref,
                           String expectedHeadRevisionId, String newHeadRevisionId) {
        if (expectedHeadRevisionId == null || expectedHeadRevisionId.isBlank()) {
            throw new IllegalArgumentException("non-genesis expected head required");
        }
        if (newHeadRevisionId == null || newHeadRevisionId.isBlank()) {
            throw new IllegalArgumentException("new head required");
        }
        return tx.update(TIMELINE_REVISION_REF)
                .set(TIMELINE_REVISION_REF.HEAD_REVISION_ID, newHeadRevisionId)
                .set(TIMELINE_REVISION_REF.VERSION, TIMELINE_REVISION_REF.VERSION.add(1L))
                .set(TIMELINE_REVISION_REF.UPDATED_AT, LocalDateTime.now())
                .where(identity(ref))
                .and(TIMELINE_REVISION_REF.HEAD_REVISION_ID.eq(expectedHeadRevisionId))
                .execute() == 1;
    }

    /** First execution of a semantic NO_OP: exact-head validation + version transition. */
    public boolean validateExpectedHead(DSLContext tx, RevisionRef ref,
                                        String expectedHeadRevisionId) {
        Condition expected = expectedHeadRevisionId == null
                ? TIMELINE_REVISION_REF.HEAD_REVISION_ID.isNull()
                : TIMELINE_REVISION_REF.HEAD_REVISION_ID.eq(expectedHeadRevisionId);
        return tx.update(TIMELINE_REVISION_REF)
                .set(TIMELINE_REVISION_REF.VERSION, TIMELINE_REVISION_REF.VERSION.add(1L))
                .set(TIMELINE_REVISION_REF.UPDATED_AT, LocalDateTime.now())
                .where(identity(ref))
                .and(expected)
                .execute() == 1;
    }

    /** Genesis only: atomically creates the target ref and publishes its root. */
    public boolean bootstrap(DSLContext tx, RevisionRef ref, String genesisRevisionId) {
        if (genesisRevisionId == null || genesisRevisionId.isBlank()) {
            throw new IllegalArgumentException("genesis revision required");
        }
        return tx.insertInto(TIMELINE_REVISION_REF)
                .set(TIMELINE_REVISION_REF.TENANT_ID, ref.tenantId())
                .set(TIMELINE_REVISION_REF.PROJECT_ID, ref.projectId())
                .set(TIMELINE_REVISION_REF.REF_ID, ref.refId())
                .set(TIMELINE_REVISION_REF.HEAD_REVISION_ID, genesisRevisionId)
                .set(TIMELINE_REVISION_REF.VERSION, 0L)
                .set(TIMELINE_REVISION_REF.UPDATED_AT, LocalDateTime.now())
                .onConflict(TIMELINE_REVISION_REF.TENANT_ID,
                        TIMELINE_REVISION_REF.PROJECT_ID, TIMELINE_REVISION_REF.REF_ID)
                .doNothing()
                .execute() == 1;
    }

    public String currentHead(DSLContext tx, RevisionRef ref) {
        return tx.select(TIMELINE_REVISION_REF.HEAD_REVISION_ID)
                .from(TIMELINE_REVISION_REF)
                .where(identity(ref))
                .fetchOne(TIMELINE_REVISION_REF.HEAD_REVISION_ID);
    }

    public String currentHead(RevisionRef ref) {
        return currentHead(dsl, ref);
    }

    private static Condition identity(RevisionRef ref) {
        if (ref == null) {
            throw new IllegalArgumentException("revision ref required");
        }
        if (!RevisionRef.MAIN_REF.equals(ref.refId())) {
            throw new IllegalArgumentException("only canonical main Timeline ref is supported");
        }
        return TIMELINE_REVISION_REF.TENANT_ID.eq(ref.tenantId())
                .and(TIMELINE_REVISION_REF.PROJECT_ID.eq(ref.projectId()))
                .and(TIMELINE_REVISION_REF.REF_ID.eq(ref.refId()));
    }
}
