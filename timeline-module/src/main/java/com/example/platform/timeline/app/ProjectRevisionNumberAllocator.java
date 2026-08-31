package com.example.platform.timeline.app;

import org.jooq.DSLContext;

import static com.example.platform.typedschema.jooq.generated.tables.ProjectRevisionCounter.PROJECT_REVISION_COUNTER;

/**
 * Timeline-owned project revision-number authority.
 *
 * <p>The absent-row bootstrap and the allocation update are deliberately two
 * statements in the caller's transaction: an atomic insert-if-absent followed
 * by the single authoritative {@code UPDATE ... RETURNING}. Every production
 * revision command path shares this type; there is no {@code MAX + 1}
 * fallback.</p>
 */
public final class ProjectRevisionNumberAllocator {

    private static final long BOOTSTRAP_VALUE = 0L;

    public long allocate(DSLContext tx, String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId required");
        }
        tx.insertInto(PROJECT_REVISION_COUNTER)
                .set(PROJECT_REVISION_COUNTER.PROJECT_ID, projectId)
                .set(PROJECT_REVISION_COUNTER.NEXT_REVISION_NUMBER, BOOTSTRAP_VALUE)
                .onConflict(PROJECT_REVISION_COUNTER.PROJECT_ID)
                .doNothing()
                .execute();

        Long allocated = tx.update(PROJECT_REVISION_COUNTER)
                .set(PROJECT_REVISION_COUNTER.NEXT_REVISION_NUMBER,
                        PROJECT_REVISION_COUNTER.NEXT_REVISION_NUMBER.add(1L))
                .where(PROJECT_REVISION_COUNTER.PROJECT_ID.eq(projectId))
                .returning(PROJECT_REVISION_COUNTER.NEXT_REVISION_NUMBER)
                .fetchOne(PROJECT_REVISION_COUNTER.NEXT_REVISION_NUMBER);
        if (allocated == null) {
            throw new IllegalStateException(
                    "project revision counter allocation returned no row: " + projectId);
        }
        return allocated;
    }
}
